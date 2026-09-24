package com.riderspay.autodriver;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
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
        int yellow = Color.rgb(255, 212, 0);
        int dark = Color.rgb(10, 10, 10);
        float density = getResources().getDisplayMetrics().density;
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(Math.round(26 * density), Math.round(28 * density), Math.round(26 * density), Math.round(28 * density));
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(dark);
        panel.setCornerRadius(28 * density);
        brand.setBackground(panel);
        brand.setElevation(10 * density);
        TextView mark = new TextView(this);
        mark.setText("AUTO");
        mark.setTextColor(dark);
        mark.setTextSize(12);
        mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        GradientDrawable markBg = new GradientDrawable();
        markBg.setColor(yellow);
        markBg.setCornerRadius(15 * density);
        mark.setBackground(markBg);
        brand.addView(mark, new LinearLayout.LayoutParams(Math.round(64 * density), Math.round(64 * density)));
        TextView name = new TextView(this);
        name.setText("RIDERS PAY");
        name.setTextColor(Color.WHITE);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setTextSize(34);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-2, -2);
        nameParams.topMargin = Math.round(18 * density);
        brand.addView(name, nameParams);
        TextView subtitle = new TextView(this);
        subtitle.setText("YOUR DIGITAL AUTO PARTNER");
        subtitle.setTextColor(yellow);
        subtitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        subtitle.setTextSize(11);
        subtitle.setGravity(Gravity.CENTER);
        brand.addView(subtitle);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(-1, -2);
        brandParams.setMargins(Math.round(24 * density), 0, Math.round(24 * density), 0);
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
