package com.riderspay.autodriver;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Brand splash, then the first-launch setup or the existing driver dashboard. */
public final class SplashActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable open = () -> {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(255, 212, 0));
        getWindow().setNavigationBarColor(Color.rgb(10, 10, 10));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(255, 212, 0));
        root.setFitsSystemWindows(true);
        int dark = Color.rgb(10, 10, 10);
        float density = getResources().getDisplayMetrics().density;
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(Math.round(10 * density), Math.round(10 * density), Math.round(10 * density), Math.round(10 * density));
        brand.setBackgroundColor(dark);
        brand.setElevation(10 * density);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.riders_pay_logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        brand.addView(logo, new LinearLayout.LayoutParams(Math.round(286 * density), Math.round(286 * density)));
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(-2, -2);
        brandParams.setMargins(Math.round(20 * density), 0, Math.round(20 * density), 0);
        root.addView(brand, brandParams);
        TextView loading = new TextView(this);
        loading.setText("READY FOR THE ROAD");
        loading.setTextColor(dark);
        loading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        loading.setTextSize(10);
        loading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(-1, -2);
        loadingParams.topMargin = Math.round(22 * density);
        root.addView(loading, loadingParams);
        setContentView(root);
        handler.postDelayed(open, 950L);
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(open);
        super.onDestroy();
    }
}
