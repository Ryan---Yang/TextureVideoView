package com.seewo.texturevideoview;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Formatter;
import java.util.Locale;
import java.util.UUID;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class TextureVideoView extends LinearLayout implements TextureView.SurfaceTextureListener, OnClickListener {

	// Log tag
	private static final String TAG = "TextureVideoView";
	private static final String DEFAULT_SCREEN_SHOT_DEST_PATH =
			Environment.getExternalStorageDirectory() + File.separator + "TextureVideoView" + File.separator + "ScreenShot" + File.separator;

	public static final boolean LOG_ON = true;
	public static final long UPDATE_INTERVAL = 200;

	private static final int SWITCH_PLAY_STATE = 1;
	private static final int BACK = 2;
	private static final int FORWARD = 3;
	private static final int SCREEN_SHOT = 4;
	private static final int DEFAULT_ACTION = -1;

	private static final int SCREEN_BTN_ENABLE = -2;
	private static final int SCREEN_BTN_DISABLED = -3;

	private static final int SWITCH_TO_PLAY = -4;
	private static final int SWITCH_TO_PAUSE = -5;

	private static final int SCREEN_SHOT_SUCCESS = -1;
	private static final int UPDATE_CUR_PROGRESS = 0;
	private static final int CHANGED_STEP = 2000;

	private Context mContext;
	private MediaPlayer mMediaPlayer;
	private IMediaPlayerListener mPlayerListener;
	private PlayProgressObserverThread mObserverThread;

	private TextView mCurrentTimeView;
	private TextView mTotalTimeView;
	private TextView mVideoTitle;
	private SeekBar mPlayProgress;
	private Button mBackBtn;
	private Button mSwitchPlayStateBtn;
	private Button mForwardBtn;
	private Button mScreenShotBtn;
	private Button mExitBtn;

	private TextureView mVideoView;

	private boolean mIsDataSourceSet;
	private boolean mIsViewAvailable;
	private boolean mIsVideoPrepared;
	private boolean mIsPlayCalled;
	private boolean mIsRunning;
	private boolean mIsSetProgress = false;

	private MediaMetadataRetriever mMediaMetadataRetriever;

	private State mState;
	private String mVideoPath;
	private String mScreenShotDesPath = DEFAULT_SCREEN_SHOT_DEST_PATH;
	StringBuilder mFormatBuilder;
	Formatter mFormatter;
	private int mCurrentPosition;
	private int mVideoTotalLength;
	private Bundle mStateBundle;
	private Rect mVedioViewRect;

	public enum State {
		UNINITIALIZED, PLAY, STOP, PAUSE, END
	}

	private Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {

			switch (msg.what) {
			case SCREEN_SHOT_SUCCESS:
				Toast.makeText(mContext, R.string.tv_save_screen_image_successful, Toast.LENGTH_SHORT).show();
				break;
			case SCREEN_BTN_ENABLE:
				mScreenShotBtn.setEnabled(true);
				break;
			case SCREEN_BTN_DISABLED:
				mScreenShotBtn.setEnabled(false);
				break;
			case SWITCH_TO_PLAY:
				mSwitchPlayStateBtn.setBackgroundResource(R.drawable.play);
				break;
			case SWITCH_TO_PAUSE:
				mSwitchPlayStateBtn.setBackgroundResource(R.drawable.video_pause);
				break;
			case UPDATE_CUR_PROGRESS:
				if (!mIsSetProgress) {
					mPlayProgress.setProgress(mCurrentPosition);
					mCurrentTimeView.setText(stringForTime(mCurrentPosition));
				}
				mPlayerListener.onProgressUpdated();
				break;
			default:
				break;
			}
		}

	};
	private OnSeekBarChangeListener mSeekBarChangeListener = new OnSeekBarChangeListener() {

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			int progress = seekBar.getProgress();

			if (mVideoView != null) {
				mCurrentPosition = progress;
				mHandler.sendEmptyMessage(UPDATE_CUR_PROGRESS);
				seekTo(progress);
				if (mState != State.PLAY) {
					mHandler.sendEmptyMessage(SCREEN_BTN_ENABLE);
				}

			}
			mIsSetProgress = false;
		}

		@Override
		public void onStartTrackingTouch(SeekBar arg0) {
			mIsSetProgress = true;
		}

		@Override
		public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {

		}
	};

	public TextureVideoView(Context context) {
		super(context);
		mContext = context;
	}

	public TextureVideoView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mContext = context;
	}

	public TextureVideoView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mContext = context;

	}

	private void initView() {
		LayoutInflater.from(mContext).inflate(R.layout.videoview, this, true);
		initPlayer();
		mCurrentTimeView = (TextView) findViewById(R.id.tv_current_play_time);
		mTotalTimeView = (TextView) findViewById(R.id.tv_total_pley_time);
		mPlayProgress = (SeekBar) findViewById(R.id.play_seek_bar);
		mBackBtn = (Button) findViewById(R.id.btn_back);
		mSwitchPlayStateBtn = (Button) findViewById(R.id.btn_switch);
		mForwardBtn = (Button) findViewById(R.id.btn_forward);
		mScreenShotBtn = (Button) findViewById(R.id.btn_shot_screen);
		mExitBtn = (Button) findViewById(R.id.btn_exit);
		mVideoView = (TextureView) findViewById(R.id.video_view);

		mVideoTitle = (TextView) findViewById(R.id.video_title);
		initListener();
	}

	private void setVideoViewRect() {
		int left = mVideoView.getLeft();
		int top = mVideoView.getTop();
		int width = mVideoView.getWidth();
		int height = mVideoView.getHeight();
		mVedioViewRect = new Rect(left, top, left + width, top + height);
		Log.v(TAG, mVedioViewRect.toShortString());
	}

	private void initPlayer() {
		if (mMediaPlayer == null) {
			mMediaPlayer = new MediaPlayer();
		} else {
			mMediaPlayer.reset();
			mCurrentPosition = 0;
		}

		initData();

	}

	private void initData() {
		mIsVideoPrepared = false;
		mIsPlayCalled = false;
		mState = State.UNINITIALIZED;
		mIsRunning = true;
		mStateBundle = new Bundle();

		mFormatBuilder = new StringBuilder();
		mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
		mMediaMetadataRetriever = new MediaMetadataRetriever();
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		initView();
	}

	public void initListener() {
		mBackBtn.setOnClickListener(this);
		mForwardBtn.setOnClickListener(this);
		mSwitchPlayStateBtn.setOnClickListener(this);
		mScreenShotBtn.setOnClickListener(this);
		mPlayProgress.setOnSeekBarChangeListener(mSeekBarChangeListener);
		mVideoView.setSurfaceTextureListener(this);
		mExitBtn.setOnClickListener(this);
	}

	/**
	 * @see android.media.MediaPlayer#setDataSource(String)
	 */
	public void setDataSource(String path) {
		initPlayer();
		mVideoPath = path;
		log("set video path:" + path);

		try {
			mMediaPlayer.setDataSource(path);
			mMediaMetadataRetriever.setDataSource(path);
			String fileName = new File(path).getName();
			mVideoTitle.setText(fileName);
			mIsDataSourceSet = true;
			prepare();
		} catch (IOException e) {
			Log.d(TAG, e.getMessage());
		}
	}

//	/**
//	 * @see android.media.MediaPlayer#setDataSource(android.content.Context, android.net.Uri)
//	 */
//	public void setDataSource(Context context, Uri uri) {
//		initPlayer();
//		mMediaMetadataRetriever.setDataSource(context, uri);
//		try {
//			mMediaPlayer.setDataSource(context, uri);
//			mIsDataSourceSet = true;
//			prepare();
//		} catch (IOException e) {
//			Log.d(TAG, e.getMessage());
//		}
//	}
//
//	/**
//	 * @see android.media.MediaPlayer#setDataSource(java.io.FileDescriptor)
//	 */
//	public void setDataSource(AssetFileDescriptor afd) {
//		initPlayer();
//
//		try {
//			long startOffset = afd.getStartOffset();
//			long length = afd.getLength();
//			mMediaPlayer.setDataSource(afd.getFileDescriptor(), startOffset, length);
//			mIsDataSourceSet = true;
//			prepare();
//		} catch (IOException e) {
//			Log.d(TAG, e.getMessage());
//		}
//	}

	private void prepare() {
		try {
			mMediaPlayer.setOnVideoSizeChangedListener(
					new MediaPlayer.OnVideoSizeChangedListener() {
						@Override
						public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {

						}
					}
					);
			mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
				@Override
				public void onCompletion(MediaPlayer mp) {
					mState = State.END;
					log("Video has ended.");
					mHandler.sendEmptyMessage(SCREEN_BTN_ENABLE);
					mHandler.sendEmptyMessage(SWITCH_TO_PLAY);
					if (mPlayerListener != null) {
						mPlayerListener.onVideoEnd(mVideoPath);
					}
				}
			});

			mMediaPlayer.prepareAsync();

			// Play video when the media source is ready for playback.
			mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
				@Override
				public void onPrepared(MediaPlayer mediaPlayer) {
					mIsVideoPrepared = true;
					if (mIsPlayCalled && mIsViewAvailable) {
						log("Player is prepared and play() was called.");
						startPlay(mCurrentPosition);
					}

					if (mPlayerListener != null) {
						mPlayerListener.onVideoPrepared();
					}
				}
			});

		} catch (IllegalArgumentException e) {
			Log.d(TAG, e.getMessage());
		} catch (SecurityException e) {
			Log.d(TAG, e.getMessage());
		} catch (IllegalStateException e) {
			Log.d(TAG, e.toString());
		}
	}

	public void startPlay(int startPoint) {
		if (!mIsDataSourceSet) {
			log("play() was called but data source was not set.");
			return;
		}
		mIsPlayCalled = true;
		mCurrentPosition = startPoint;
		if (!mIsVideoPrepared) {
			log("play() was called but video is not prepared yet, waiting.");
			return;
		}

		if (!mIsViewAvailable) {
			log("play() was called but view is not available yet, waiting.");
			return;
		}

		mVideoTotalLength = getDuration();
		mTotalTimeView.setText(stringForTime(mVideoTotalLength));
		mIsPlayCalled = true;
		mCurrentPosition = startPoint;
		mVideoTotalLength = getDuration();
		mPlayProgress.setMax(mVideoTotalLength);
		mState = State.PLAY;
		mObserverThread = new PlayProgressObserverThread();
		seekTo(mCurrentPosition);
		mMediaPlayer.start();
		mPlayerListener.onStartPlay();
		new Thread(mObserverThread).start();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		int eventX = (int) event.getX();
		int eventY = (int) event.getY();
		boolean isTouchVideoView = mVedioViewRect.contains(eventX, eventY);
		if (isTouchVideoView && event.getAction() == MotionEvent.ACTION_DOWN) {

			if (mVideoTitle.getVisibility() == View.GONE) {
				mVideoTitle.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_in));
				mVideoTitle.setVisibility(View.VISIBLE);

			} else {
				mVideoTitle.startAnimation(AnimationUtils.loadAnimation(mContext, android.R.anim.fade_out));
				mVideoTitle.setVisibility(View.GONE);

			}
			return true;
		} else {
			return super.onTouchEvent(event);
		}

	}

	public void switchPlayState() {
		log("switchPlayState()");
		if (mState == State.PLAY) {
			mMediaPlayer.pause();
			mState = State.PAUSE;
			mHandler.sendEmptyMessage(SWITCH_TO_PLAY);
			mHandler.sendEmptyMessage(SCREEN_BTN_ENABLE);
			mPlayerListener.onPaused(mVideoPath, mCurrentPosition);
		} else if (mState == State.PAUSE || mState == State.END || mState == State.STOP) {
			mMediaPlayer.start();
			mState = State.PLAY;
			mHandler.sendEmptyMessage(SWITCH_TO_PAUSE);
			mHandler.sendEmptyMessage(SCREEN_BTN_DISABLED);
		}
	}

	public void stop() {
		if (mState == State.STOP) {
			log("stop() was called but video already stopped.");
			return;
		}

		if (mState == State.END) {
			log("stop() was called but video already ended.");
			return;
		}
		mPlayerListener.onPaused(mVideoPath, mCurrentPosition);
		mState = State.STOP;
		if (mMediaPlayer.isPlaying()) {
			mMediaPlayer.pause();
			mCurrentPosition = 100;
			seekTo(mCurrentPosition);
		}
	}

	public void quickPlay(int flag) {
		int nextPoint = 0;
		switch (flag) {
		case BACK:
			nextPoint = mCurrentPosition - CHANGED_STEP;
			if (nextPoint < 0) {
				nextPoint = 0;
			}

			break;
		case FORWARD:
			nextPoint = mCurrentPosition + CHANGED_STEP;
			if (nextPoint > mVideoTotalLength) {
				return;
			}
			break;
		default:
			break;
		}
		if (mState != State.PLAY) {
			mHandler.sendEmptyMessage(SCREEN_BTN_ENABLE);
		}
		seekTo(nextPoint);
		mPlayProgress.setProgress(nextPoint);
		mCurrentPosition = nextPoint;
		mHandler.sendEmptyMessage(UPDATE_CUR_PROGRESS);
	}

	public void setLooping(boolean looping) {
		mMediaPlayer.setLooping(looping);
	}

	public void seekTo(int milliseconds) {
		mMediaPlayer.seekTo(milliseconds);
	}

	public int getDuration() {
		return mMediaPlayer.getDuration();
	}

	static void log(String message) {
		if (LOG_ON) {
			Log.d(TAG, message);
		}
	}

	public void setMeidaPlayerListener(IMediaPlayerListener listener) {
		mPlayerListener = listener;
	}

	public void screenShot() {
		if (null != mVideoPath) {
			log("screenShot");
			new Thread(new Runnable() {
				@Override
				public void run() {
					final Bitmap bitmap =
							mMediaMetadataRetriever.getFrameAtTime(mMediaPlayer.getCurrentPosition() * 1000,
									MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
					saveShotImage(bitmap);

				}
			}).start();

		} else {
			Log.e(TAG, "mCurrentPath is null");
		}
	}

	private void saveShotImage(Bitmap pBitmap) {
		Log.v(TAG, "bitmap.size():" + pBitmap.getByteCount() / 1024);
		BufferedOutputStream lBos = null;
		try {
			String savePath = getShotImagePath();
			lBos = new BufferedOutputStream(new FileOutputStream(savePath));
			if (!pBitmap.isRecycled())
				pBitmap.compress(Bitmap.CompressFormat.PNG, 100, lBos);
			lBos.flush();
			Log.v(TAG, "file save succeed");
			mHandler.sendEmptyMessage(SCREEN_SHOT_SUCCESS);
			mPlayerListener.onScreenShotFinished(savePath);
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(TAG, "file save failed");
		} finally {
			if (null != lBos) {
				try {
					lBos.close();
				} catch (Exception e2) {
					e2.printStackTrace();
				}
			}
		}
	}

	public void storeState() {
		log("video pause");
		int stopTime = mMediaPlayer.getCurrentPosition();
		boolean isPlaying = (mState == State.PLAY ? true : false);

		mStateBundle.putBoolean("isPlaying", isPlaying);
		mStateBundle.putInt("stopTime", stopTime);
		stop();
	}

	public void restoreState() {
		log("restore the state of last exit time");
		mCurrentPosition = mStateBundle.getInt("stopTime");
		boolean isPlaying = mStateBundle.getBoolean("isPlaying");
		if (isPlaying) {
			mState = State.PLAY;
			mMediaPlayer.seekTo(mCurrentPosition);
			mMediaPlayer.start();
		} else {
			mState = State.PAUSE;
			mMediaPlayer.seekTo(mCurrentPosition);
			mMediaPlayer.pause();
		}
	}

	private String getShotImagePath() {
		File file = new File(mScreenShotDesPath);
		if (!file.exists()) {
			file.mkdirs();
		}
		return mScreenShotDesPath + UUID.randomUUID().toString() + ".png";
	}

	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
		Surface surface = new Surface(surfaceTexture);
		mMediaPlayer.setSurface(surface);
		mIsViewAvailable = true;
		setVideoViewRect();
		if (mIsDataSourceSet && mIsPlayCalled && mIsVideoPrepared) {
			log("View is available and play() was called.");
			startPlay(mCurrentPosition);
		}
	}

	private String stringForTime(int milesecond) {

		int second = milesecond % 1000 >= 500 ? milesecond / 1000 + 1 : milesecond / 1000;

		int hour = second / 3600;
		int min = second / 60;
		int sec = second % 60;
		StringBuilder sBuilder = new StringBuilder();

		if (hour != 0) {
			sBuilder.append(hour + ":");
		}

		if (min < 10) {
			sBuilder.append("0" + String.valueOf(min) + ":");
		} else {
			sBuilder.append(String.valueOf(min) + ":");
		}
		if (sec < 10) {
			sBuilder.append("0" + String.valueOf(sec));
		} else {
			sBuilder.append(String.valueOf(sec));
		}

		return sBuilder.toString();
	}

	/**
	 * 设置截图的存储路径，如果没有设置，默认保存在 /storage/sdcard0/ExpandVideoView/ScreenShot/目录下
	 * 
	 * @param desPath 目标存储路径
	 */
	public void setScreenShotSavePath(String desPath) {
		mScreenShotDesPath = desPath;
	}

	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
		return false;
	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture surface) {

	}

	@Override
	public void onClick(View v) {
		int viewId = v.getId();
		if (viewId == R.id.btn_switch) {
			switchPlayState();
		} else if (viewId == R.id.btn_back) {
			quickPlay(BACK);
		} else if (viewId == R.id.btn_forward) {
			quickPlay(FORWARD);
		} else if (viewId == R.id.btn_shot_screen) {
			screenShot();
			mHandler.sendEmptyMessage(SCREEN_BTN_DISABLED);
		} else if (viewId == R.id.btn_exit) {
			stop();
			mPlayerListener.onClickExit();
		}
	}

	private class PlayProgressObserverThread implements Runnable {

		@Override
		public void run() {
			Log.v(TAG, "observer thread start");
			while (mIsRunning) {
				if (mState == State.PLAY) {
					mCurrentPosition = mMediaPlayer.getCurrentPosition();
					mHandler.sendEmptyMessage(UPDATE_CUR_PROGRESS);
				}
				try {
					Thread.sleep(UPDATE_INTERVAL);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			Log.v(TAG, "observer thread exit");
		}
	}

	public interface IMediaPlayerListener {

		public void onVideoPrepared();

		public void onVideoEnd(String pVideoPath);

		public void onProgressUpdated();

		public void onScreenShotFinished(String pImagePath);

		public void onPaused(String pVideoPath, int pTime);

		public void onStartPlay();

		public void onClickExit();

	}

}
