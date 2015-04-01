package org.pyneo.fieldunlock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;


public class ImageUtils implements Util {

	// Bitmap (blurring) function using ScriptIntrinsicBlur (API 17+), returns blurred Bitmap
	// Takes radius 1-25
	public static Bitmap fastBlur(Context context, Bitmap sentBitmap, int radius) {
		float BITMAP_SCALE = 0.1f;
		float BLUR_RADIUS = (float) radius;

		if (Build.VERSION.SDK_INT > 16) {
			int width = Math.round(sentBitmap.getWidth() * BITMAP_SCALE);
			int height = Math.round(sentBitmap.getHeight() * BITMAP_SCALE);

			Bitmap inputBitmap = null;
			try {
				inputBitmap = Bitmap.createScaledBitmap(sentBitmap, width, height, false);

			} catch (Exception e) {
				Log.e(TAG, e.toString(), e);
				return null;
			}

			Bitmap outputBitmap = null;
			try {
				if (inputBitmap != null)
					outputBitmap = Bitmap.createBitmap(inputBitmap);

			} catch (Exception e) {
				Log.e(TAG, e.toString(), e);
				return null;
			}

			if (outputBitmap != null) {
				RenderScript rs = RenderScript.create(context);
				ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
				Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
				Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);

				theIntrinsic.setRadius(BLUR_RADIUS);
				theIntrinsic.setInput(tmpIn);
				theIntrinsic.forEach(tmpOut);
				tmpOut.copyTo(outputBitmap);

				return outputBitmap;
			}
		}

		return null;
	}


	// Bitmap function that turns the passed 'drawable' into a Bitmap
	public static Bitmap drawableToBitmap(Drawable drawable) {
		if (drawable instanceof BitmapDrawable) {
			return ((BitmapDrawable) drawable).getBitmap();
		}

		Bitmap bitmap = null;
		if (!(drawable instanceof ColorDrawable)) {
			try {
				bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);

			} catch (IllegalArgumentException e) {
				Log.e(TAG, e.toString(), e);
				return null;
			}

			Canvas canvas = new Canvas(bitmap);
			drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
			drawable.draw(canvas);
		}

		return bitmap;
	}


	// Function to store passed Bitmap as .png on external storage
	public static boolean storeImage(Bitmap image) {
		if (isExternalStorageWritable()) {
			File pictureFile = new File(Environment.getExternalStorageDirectory() + "/FieldUnlock/blurredWallpaper.png");

			try {
				FileOutputStream fos = new FileOutputStream(pictureFile);
				image.compress(Bitmap.CompressFormat.PNG, 90, fos);
				fos.flush();
				fos.close();

			} catch (FileNotFoundException e) {
				Log.e(TAG, e.toString(), e);
			} catch (IOException e) {
				Log.e(TAG, e.toString(), e);
			}

			return true;
		}

		return false;
	}


	// Boolean function to check whether a blurred wallpaper png exists or not
	public static boolean doesBlurredWallpaperExist() {
		File blurredWallpaper = new File(Environment.getExternalStorageDirectory() + "/FieldUnlock/blurredWallpaper.png");

		return blurredWallpaper.exists();
	}


	// Drawable function that returns the blurred wallpaper png as Drawable
	public static Drawable retrieveWallpaperDrawable() {
		if (isExternalStorageReadable())
			return Drawable.createFromPath(Environment.getExternalStorageDirectory() + "/FieldUnlock/blurredWallpaper.png");

		return null;
	}


	// Return true if external storage is writable, false otherwise
	public static boolean isExternalStorageWritable() {
		String state = Environment.getExternalStorageState();

		return Environment.MEDIA_MOUNTED.equals(state);
	}

	// Return true if external storage is readable, false otherwise
	public static boolean isExternalStorageReadable() {
		String state = Environment.getExternalStorageState();

		return Environment.MEDIA_MOUNTED.equals(state) ||
				Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
	}

	public static Bitmap doBrightness(Bitmap src, int value) {
		// image size
		int width = src.getWidth();
		int height = src.getHeight();
		// create output bitmap
		Bitmap bmOut = Bitmap.createBitmap(width, height, src.getConfig());
		// color information
		int A, R, G, B;
		int pixel;
		// scan through all pixels
		for(int x = 0; x < width; ++x) {
			for(int y = 0; y < height; ++y) {
				// get pixel color
				pixel = src.getPixel(x, y);
				A = Color.alpha(pixel);
				R = Color.red(pixel);
				G = Color.green(pixel);
				B = Color.blue(pixel);
				// increase/decrease each channel
				R += value;
				if(R > 255) { R = 255; }
				else if(R < 0) { R = 0; }
				G += value;
				if(G > 255) { G = 255; }
				else if(G < 0) { G = 0; }
				B += value;
				if(B > 255) { B = 255; }
				else if(B < 0) { B = 0; }
				// apply new pixel color to output bitmap
				bmOut.setPixel(x, y, Color.argb(A, R, G, B));
			}
		}
		// return final image
		return bmOut;
	}
}
