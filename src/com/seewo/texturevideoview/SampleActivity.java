package com.seewo.texturevideoview;

import java.io.File;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;

import com.seewo.texturevideoview.TextureVideoView.IMediaPlayerListener;

public class SampleActivity extends Activity {

	// Video file url
	// private static final String FILE_URL = "http://www.w3schools.com/html/mov_bbb.mp4";
	private static final String FILE_PATH = Environment.getExternalStorageDirectory() + File.separator + "test.3gp";
	private TextureVideoView mTextureVideoView;
	private IMediaPlayerListener mListener = new IMediaPlayerListener() {

		@Override
		public void onVideoPrepared() {
			// TODO Auto-generated method stub

		}

		@Override
		public void onVideoEnd(String pVideoPath) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onStartPlay() {
			// TODO Auto-generated method stub

		}

		@Override
		public void onScreenShotFinished(String pImagePath) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onProgressUpdated() {
			// TODO Auto-generated method stub

		}

		@Override
		public void onPaused(String pVideoPath, int pTime) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onClickExit() {
			// TODO Auto-generated method stub
			finish();
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		initView();
		mTextureVideoView.setDataSource(FILE_PATH);
		mTextureVideoView.setMeidaPlayerListener(mListener);
		mTextureVideoView.startPlay(0);
	}

	private void initView() {
		mTextureVideoView = (TextureVideoView) findViewById(R.id.cropTextureView);
	}

}
