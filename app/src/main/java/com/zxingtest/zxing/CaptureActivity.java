package com.zxingtest.zxing;

import java.io.IOException;
import java.lang.reflect.Field;

import com.google.zxing.Result;
import com.zxingtest.R;
import com.zxingtest.zxing.Camera.CameraManager;
import com.zxingtest.zxing.tools.BeepManager;
import com.zxingtest.zxing.tools.CaptureActivityHandler;
import com.zxingtest.zxing.tools.DecodeThread;
import com.zxingtest.zxing.tools.InactivityTimer;
import com.zxingtest.zxing.tools.ScanningImageTools;
import com.zxingtest.zxing.tools.ScanningImageTools.IZCodeCallBack;
import com.zxingtest.zxing.tools.ToastUtil;
import com.zxingtest.zxing.tools.Utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class CaptureActivity extends Activity implements SurfaceHolder.Callback {

	protected TextView mytoolBarTitle;

	private static final String TAG = CaptureActivity.class.getSimpleName();

	private CameraManager cameraManager;
	private CaptureActivityHandler handler;
	private InactivityTimer inactivityTimer;
	private BeepManager beepManager;

	private SurfaceView scanPreview = null;
	private RelativeLayout scanContainer;
	private RelativeLayout scanCropView;
	private ImageView scanLine;
	private static final int REQUEST_CODE = 110;
	String photo_path = "";
	Bitmap scanBitmap;
	TextView caputer_back;

	private Rect mCropRect = null;

	public Handler getHandler() {
		return handler;
	}

	public CameraManager getCameraManager() {
		return cameraManager;
	}

	private boolean isHasSurface = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);

		setContentView(R.layout.capture_main);
		initCaptureView();
	}

	private void initCaptureView() {

		caputer_back = (TextView) findViewById(R.id.caputer_back);
		findViewById(R.id.caputer_right).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				selectQCoder();
			}
		});
		caputer_back.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				onBackPressed();
			}
		});

		scanPreview = (SurfaceView) findViewById(R.id.capture_preview);
		scanContainer = (RelativeLayout) findViewById(R.id.capture_container);
		scanCropView = (RelativeLayout) findViewById(R.id.capture_crop_view);
		scanLine = (ImageView) findViewById(R.id.capture_scan_line);

		inactivityTimer = new InactivityTimer(this);
		beepManager = new BeepManager(this);

		TranslateAnimation animation = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f,
				Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT,
				0.98f);
		animation.setDuration(4500);
		animation.setRepeatCount(-1);
		animation.setRepeatMode(Animation.RESTART);
		scanLine.startAnimation(animation);

	}

	/**
	 * 图库中选择二维码
	 */
	protected void selectQCoder() {
		// TODO Auto-generated method stub
		Intent innerIntent = new Intent(); // "android.intent.action.GET_CONTENT"
		if (Build.VERSION.SDK_INT < 19) {
			innerIntent.setAction(Intent.ACTION_GET_CONTENT);
		} else {
			innerIntent.setAction(Intent.ACTION_OPEN_DOCUMENT);
		}

		innerIntent.setType("image/*");
		Intent wrapperIntent = Intent.createChooser(innerIntent, "选择二维码图片");

		CaptureActivity.this.startActivityForResult(wrapperIntent, REQUEST_CODE);
	}

	/**
	 * 处理返回二维码图片
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == RESULT_OK) {

			switch (requestCode) {

			case REQUEST_CODE:

				String[] proj = { MediaStore.Images.Media.DATA };
				// 获取选中图片的路径
				Cursor cursor = null;
				try {
					cursor = getContentResolver().query(data.getData(), proj, null, null, null);

					if (cursor.moveToFirst()) {

						int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
						photo_path = cursor.getString(column_index);
						if (photo_path == null) {
							photo_path = Utils.getPath(getApplicationContext(), data.getData());
							Log.i("123path  Utils", photo_path);
						}
						Log.i("123path", photo_path);

					}
				} catch (Exception e) {
				} finally {
					if (cursor != null) {
						cursor.close();
					}
				}

				ScanningImageTools.scanningImage(photo_path, new IZCodeCallBack() {

					@Override
					public void ZCodeCallBackUi(Result result) {
						// TODO Auto-generated method stub
						if (result == null) {
							Looper.prepare();
							Toast.makeText(getApplicationContext(), "未识别到二维码", Toast.LENGTH_SHORT).show();
							Looper.loop();
						} else {
							Log.i("123result", result.toString());
							// Log.i("123result", result.getText());
							// 数据返回进行仿乱码处理
							final String recode = ScanningImageTools.recode(result.toString());

							doResult(recode);
						}

					}
				});
				break;
			}

		}

	}

	@Override
	protected void onResume() {
		super.onResume();

		if (cameraManager == null) {

			cameraManager = new CameraManager(getApplication());
		}

		if (isHasSurface) {
			initCamera(scanPreview.getHolder());
		} else {
			scanPreview.getHolder().addCallback(this);
		}

		inactivityTimer.onResume();
	}

	@Override
	protected void onPause() {
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		inactivityTimer.onPause();
		beepManager.close();
		cameraManager.closeDriver();
		if (!isHasSurface) {
			scanPreview.getHolder().removeCallback(this);
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		if (scanPreview != null) {

			scanPreview.getHolder().removeCallback(this);
		}

		inactivityTimer.shutdown();
		super.onDestroy();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (holder == null) {
			Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
		}
		if (!isHasSurface) {
			isHasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		isHasSurface = false;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

	}

	public void handleDecode(Result rawResult, Bundle bundle) {
		inactivityTimer.onActivity();
		beepManager.playBeepSoundAndVibrate();

		ToastUtil.showShort(CaptureActivity.this, rawResult.getText());
		doResult(rawResult.getText());

	}

	private void initCamera(SurfaceHolder surfaceHolder) {
		if (surfaceHolder == null) {
			throw new IllegalStateException("No SurfaceHolder provided");
		}
		if (cameraManager.isOpen()) {
			Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
			return;
		}

		try {
			cameraManager.openDriver(surfaceHolder);
			// Creating the handler starts the preview, which can also throw a
			// RuntimeException.
			if (handler == null) {
				handler = new CaptureActivityHandler(this, cameraManager, DecodeThread.ALL_MODE);
			}

			initCrop();
		} catch (IOException ioe) {
			Log.w(TAG, ioe);
			displayFrameworkBugMessageAndExit();
		} catch (RuntimeException e) {
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			Log.w(TAG, "Unexpected error initializing camera", e);
			displayFrameworkBugMessageAndExit();
		}
	}

	private void displayFrameworkBugMessageAndExit() {
		// camera error
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.app_name));
		builder.setMessage("相机打开出错，请稍后重试");
		builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				finish();
			}

		});
		builder.setOnCancelListener(new DialogInterface.OnCancelListener() {

			@Override
			public void onCancel(DialogInterface dialog) {
				dialog.dismiss();
				finish();
			}
		});
		builder.show();
	}

	public void restartPreviewAfterDelay(long delayMS) {
		if (handler != null) {
			handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
		}
	}

	public Rect getCropRect() {
		return mCropRect;
	}

	/**
	 * 初始化截取的矩形区域
	 */
	private void initCrop() {
		int cameraWidth = cameraManager.getCameraResolution().y;
		int cameraHeight = cameraManager.getCameraResolution().x;

		/** 获取布局中扫描框的位置信息 */
		int[] location = new int[2];
		scanCropView.getLocationInWindow(location);

		int cropLeft = location[0];
		int cropTop = location[1] - getStatusBarHeight();

		int cropWidth = scanCropView.getWidth();
		int cropHeight = scanCropView.getHeight();

		/** 获取布局容器的宽高 */
		int containerWidth = scanContainer.getWidth();
		int containerHeight = scanContainer.getHeight();

		/** 计算最终截取的矩形的左上角顶点x坐标 */
		int x = cropLeft * cameraWidth / containerWidth;
		/** 计算最终截取的矩形的左上角顶点y坐标 */
		int y = cropTop * cameraHeight / containerHeight;

		/** 计算最终截取的矩形的宽度 */
		int width = cropWidth * cameraWidth / containerWidth;
		/** 计算最终截取的矩形的高度 */
		int height = cropHeight * cameraHeight / containerHeight;

		/** 生成最终的截取的矩形 */
		mCropRect = new Rect(x, y, width + x, height + y);
	}

	private int getStatusBarHeight() {
		try {
			Class<?> c = Class.forName("com.android.internal.R$dimen");
			Object obj = c.newInstance();
			Field field = c.getField("status_bar_height");
			int x = Integer.parseInt(field.get(obj).toString());
			return getResources().getDimensionPixelSize(x);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * 获取扫描结果并跳转H5商品详情
	 * 
	 * @param url
	 */
	public void doResult(String result) {
		if (checkReslut(result)) {

			ToastUtil.showShort(CaptureActivity.this, "识别到二维码---->" + result);

		} else {
			try {
				ToastUtil.showShort(CaptureActivity.this, "未识别到二维码");
				Thread.sleep(2000);
				if (handler == null) {
					handler = new CaptureActivityHandler(this, cameraManager, DecodeThread.ALL_MODE);
				}
				handler.restartPreviewAndDecode();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/**
	 * 检测结果
	 * 
	 * @param reslut
	 * @return
	 */
	private boolean checkReslut(String reslut) {
		// TODO Auto-generated method stub
		return true;
	}

}