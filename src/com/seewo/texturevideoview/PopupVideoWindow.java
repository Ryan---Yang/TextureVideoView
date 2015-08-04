
package com.seewo.texturevideoview;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.PopupWindow;

/**
 * @file PopupVideoWindow.java
 * @brief
 * @date 2015-1-13 create
 * 
 */
public class PopupVideoWindow {
	private Context mContext;

	private TextureVideoView mVideoView;

	private int mWindowWidth = LayoutParams.MATCH_PARENT;
	private int mWindowHeight = LayoutParams.MATCH_PARENT;

	public PopupVideoWindow(Context pContext) {
		mContext = pContext;
	}

	public PopupWindow makeVideoWindow() {
		final PopupWindow mVideoWindow;
		View mView = LayoutInflater.from(mContext).inflate(R.layout.main, null);
		mVideoView = (TextureVideoView) mView.findViewById(R.id.cropTextureView);
		mVideoWindow = new PopupWindow(mView);

		mVideoWindow.setWidth(600);
		mVideoWindow.setHeight(400);
		mVideoWindow.setFocusable(false);
		mVideoWindow.setTouchable(true);
		mVideoWindow.setOutsideTouchable(true);
		mVideoWindow.update();

		return mVideoWindow;
	}

	private void setDataPath(String path) {
		mVideoView.setDataSource(path);
	}

	public void startPlay(String path) {
		setDataPath(path);
		mVideoView.startPlay(0);
	}

	public void setWindowSize(int width, int height) {
		mWindowWidth = width;
		mWindowHeight = height;
	}
}
