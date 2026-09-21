package com.bkrides.riderpay;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import android.webkit.WebView;
import android.webkit.WebSettings;
import java.net.URLEncoder;
import org.json.JSONObject;
import java.util.Locale;

public class FloatingTripService extends Service implements LocationListener {
    public static final String PREFS = "rider_trip_service";
    public static final String ACTION_DUTY_ON = "rider.DUTY_ON";
    public static final String ACTION_DUTY_OFF = "rider.DUTY_OFF";
    public static final String ACTION_START_RIDE = "rider.START";
    public static final String ACTION_PICKED = "rider.PICKED";
    public static final String ACTION_WAITING = "rider.WAITING";
    public static final String ACTION_END = "rider.END";
    public static final String ACTION_RESET = "rider.RESET";
    public static final String ACTION_CONFIG = "rider.CONFIG";

    private static final String CHANNEL = "rider_duty";
    private static final int NOTIFICATION_ID = 5011;

    private WindowManager wm;
    private LinearLayout root, menu, qrPanel;
    private TextView bubble, qrAmount, qrPayee;
    private Button startBtn, pickedBtn, waitBtn, amountBtn, qrBtn, qrCloseBtn, qrOpenBtn;
    private WindowManager.LayoutParams lp;
    private WebView qrWeb;
    private LocationManager lm;
    private Location last;
    private Handler handler;
    private Runnable ticker;

    private SharedPreferences p;
    private String state = "IDLE";
    private boolean waiting = false;
    private double pickupMeters = 0, rideMeters = 0;
    private long startedAt = 0, pickedAt = 0, waitAccumMs = 0, waitStartedAt = 0;
    private double manualAmount = -1;

    @Override public void onCreate() {
        super.onCreate();
        p = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadState();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        lm = (LocationManager)getSystemService(LOCATION_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        ticker = new Runnable(){ public void run(){ updateBubble(); handler.postDelayed(this,1000); } };
        if (Settings.canDrawOverlays(this)) createOverlay();
        if (!"IDLE".equals(state) && !"REVIEW".equals(state)) startLocation();
        p.edit().putBoolean("duty", true).apply();
        handler.post(ticker);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? ACTION_DUTY_ON : intent.getAction();
        if (ACTION_DUTY_OFF.equals(a)) { dutyOff(); return START_NOT_STICKY; }
        if (!Settings.canDrawOverlays(this)) { dutyOff(); return START_NOT_STICKY; }
        p.edit().putBoolean("duty", true).apply();
        if (root == null) createOverlay();
        if (ACTION_START_RIDE.equals(a)) startRide();
        else if (ACTION_PICKED.equals(a)) pickedCustomer();
        else if (ACTION_WAITING.equals(a)) toggleWaiting();
        else if (ACTION_END.equals(a)) endTrip();
        else if (ACTION_RESET.equals(a)) resetTrip();
        else if (ACTION_CONFIG.equals(a)) updateBubble();
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "RIDER'S PAY duty", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Keeps trip distance and floating fare active while on duty.");
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 41, open, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder b = Build.VERSION.SDK_INT>=26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setContentTitle("RIDER'S PAY • Duty ON")
         .setContentText("Floating fare is active. Tap to open RIDER'S PAY.")
         .setSmallIcon(android.R.drawable.ic_menu_mylocation)
         .setOngoing(true).setContentIntent(pi);
        return b.build();
    }

    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
    private GradientDrawable bg(int color, float radius, int strokeColor) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp((int)radius)); d.setStroke(dp(1),strokeColor); return d;
    }

    private void createOverlay() {
        if (root != null || !Settings.canDrawOverlays(this)) return;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);

        bubble = new TextView(this);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTextColor(0xff111827);
        bubble.setTextSize(14);
        bubble.setTypeface(null, android.graphics.Typeface.BOLD);
        bubble.setLines(2);
        bubble.setBackground(bg(0xfff2b900,39,0xff6b4f00));
    bubble.setElevation(dp(8));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(82),dp(82));
        root.addView(bubble,bp);

        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(8),0,0,0);
        menu.setVisibility(View.GONE);
        startBtn = actionButton("START",0xff166534);
        pickedBtn = actionButton("PICKED",0xff1d4ed8);
        waitBtn = actionButton("WAIT",0xff92400e);
        amountBtn = actionButton("SET ₹",0xffa16207);
    qrBtn = actionButton("QR PAY",0xff7c3aed);
        menu.addView(startBtn,new LinearLayout.LayoutParams(dp(74),dp(48)));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(74),dp(48));mp.topMargin=dp(5);menu.addView(pickedBtn,mp);
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(dp(74),dp(48));wp.topMargin=dp(5);menu.addView(waitBtn,wp);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(74),dp(48));ap.topMargin=dp(5);menu.addView(amountBtn,ap);
    LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(dp(74),dp(48));qp.topMargin=dp(5);menu.addView(qrBtn,qp);
        root.addView(menu);

        qrPanel = new LinearLayout(this);
        qrPanel.setOrientation(LinearLayout.VERTICAL);
        qrPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        qrPanel.setPadding(dp(10),dp(10),dp(10),dp(10));
        qrPanel.setBackground(bg(0xff111827,18,0xff374151));
        qrPanel.setVisibility(View.GONE);

        qrAmount = new TextView(this);
        qrAmount.setTextColor(Color.WHITE);
        qrAmount.setTextSize(20);
        qrAmount.setTypeface(null, android.graphics.Typeface.BOLD);
        qrAmount.setGravity(Gravity.CENTER);
        qrPanel.addView(qrAmount,new LinearLayout.LayoutParams(dp(250),dp(36)));

        qrPayee = new TextView(this);
        qrPayee.setTextColor(0xffcbd5e1);
        qrPayee.setTextSize(10);
        qrPayee.setGravity(Gravity.CENTER);
        qrPayee.setSingleLine(true);
        qrPanel.addView(qrPayee,new LinearLayout.LayoutParams(dp(250),dp(28)));

        qrWeb = new WebView(this);
        qrWeb.setBackgroundColor(Color.WHITE);
        WebSettings qrs = qrWeb.getSettings();
        qrs.setJavaScriptEnabled(true);
        qrs.setAllowFileAccess(true);
        qrs.setAllowContentAccess(false);
        qrPanel.addView(qrWeb,new LinearLayout.LayoutParams(dp(250),dp(250)));

        LinearLayout qActions = new LinearLayout(this);
        qActions.setOrientation(LinearLayout.HORIZONTAL);
        qActions.setGravity(Gravity.CENTER);
        qActions.setPadding(0,dp(7),0,0);
        qrOpenBtn = actionButton("OPEN APP",0xff0369a1);
        qrCloseBtn = actionButton("CLOSE",0xff374151);
        qActions.addView(qrOpenBtn,new LinearLayout.LayoutParams(dp(112),dp(44)));
        LinearLayout.LayoutParams qcp=new LinearLayout.LayoutParams(dp(92),dp(44));qcp.leftMargin=dp(7);qActions.addView(qrCloseBtn,qcp);
        qrPanel.addView(qActions);
        root.addView(qrPanel);

        int type = Build.VERSION.SDK_INT>=26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START;
        lp.x=p.getInt("bubbleX",18); lp.y=p.getInt("bubbleY",220);
        wm.addView(root,lp);

        startBtn.setOnClickListener(v->startRide());
        pickedBtn.setOnClickListener(v->pickedCustomer());
        waitBtn.setOnClickListener(v->toggleWaiting());
        qrBtn.setOnClickListener(v->showPaymentQr());
        qrCloseBtn.setOnClickListener(v->hidePaymentQr());
        qrOpenBtn.setOnClickListener(v->openMainApp());

        bubble.setOnTouchListener(new View.OnTouchListener() {
            float downX,downY; int startX,startY; long downT;
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getRawX();downY=e.getRawY();startX=lp.x;startY=lp.y;downT=System.currentTimeMillis();bubble.animate().scaleX(.88f).scaleY(.88f).alpha(.82f).setDuration(90).start();return true;}
                if(e.getAction()==MotionEvent.ACTION_MOVE){lp.x=startX+(int)(e.getRawX()-downX);lp.y=startY+(int)(e.getRawY()-downY);try{wm.updateViewLayout(root,lp);}catch(Exception ignored){}return true;}
                if(e.getAction()==MotionEvent.ACTION_UP){
                    float dx=Math.abs(e.getRawX()-downX),dy=Math.abs(e.getRawY()-downY);
                    p.edit().putInt("bubbleX",lp.x).putInt("bubbleY",lp.y).apply();
                    if(dx<12&&dy<12&&System.currentTimeMillis()-downT<500) { if(qrPanel!=null&&qrPanel.getVisibility()==View.VISIBLE) hidePaymentQr(); else menu.setVisibility(menu.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE); }
                    return true;
                }
                return false;
            }
        });
        updateBubble();
    }

    private void showManualAmountDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Enter final fare amount");
        if (manualAmount > 0) input.setText(String.format(Locale.US,"%.2f",manualAmount));
        new android.app.AlertDialog.Builder(this)
            .setTitle("Set fare amount")
            .setMessage("This overrides the calculated fare for the current ride and QR.")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setNeutralButton("USE CALCULATED", (d,w)->{ manualAmount=-1; p.edit().putFloat("manualAmount",-1f).apply(); updateBubble(); })
            .setPositiveButton("SET", (d,w)->{
                try {
                    double v=Double.parseDouble(input.getText().toString().trim());
                    if(v<=0||v>99999){toast("Enter a valid amount");return;}
                    manualAmount=v; p.edit().putFloat("manualAmount",(float)v).apply(); updateBubble(); toast("Manual fare set");
                } catch(Exception e){ toast("Enter a valid amount"); }
            }).show();
    }

    private boolean validUpi(String v) {
        return v != null && v.trim().matches("^[A-Za-z0-9._-]{2,}@[A-Za-z0-9._-]{2,}$");
    }

    private void openMainApp() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch(Exception ignored) {}
    }

    private void hidePaymentQr() {
        if(qrPanel!=null) qrPanel.setVisibility(View.GONE);
        if(qrWeb!=null) {
            try { qrWeb.loadUrl("about:blank"); } catch(Exception ignored) {}
        }
    }

    private void showPaymentQr() {
        if("IDLE".equals(state)) { toast("Start the ride first"); return; }
        if("PICKUP".equals(state)) { toast("Tap PICKED after the customer boards"); return; }
        if("RIDING".equals(state)) endTrip();
        if(!"REVIEW".equals(state)) return;

        double amount = totalFare();
        if(amount <= 0.0) { toast("Fare is ₹0.00"); return; }

        // Cashfree payments must go through the secure Riders Pay web/backend flow.
        // Do not generate a direct rider UPI QR here because that would bypass
        // the platform split/commission. The web app reads the native trip snapshot
        // on resume and prepares the Cashfree checkout using the final fare.
        p.edit().putFloat("cashfreePendingAmount", (float)amount).apply();
        if(menu != null) menu.setVisibility(View.GONE);
        hidePaymentQr();
        toast("Opening RIDER'S PAY for Cashfree payment");
        openMainApp();
    }

    private Button actionButton(String text,int color){
        Button b=new Button(this);b.setText(text);b.setTextSize(10);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setPadding(2,0,2,0);b.setBackground(bg(color,24,0x55ffffff));return b;
    }

    private void startRide() {
        if (!"IDLE".equals(state) && !"REVIEW".equals(state)) return;
        state="PICKUP";waiting=false;pickupMeters=0;rideMeters=0;manualAmount=-1;startedAt=System.currentTimeMillis();pickedAt=0;waitAccumMs=0;waitStartedAt=0;last=null;
        saveState();startLocation();updateBubble();toast("Ride started • pickup distance running");
    }

    private void pickedCustomer() {
        if (!"PICKUP".equals(state)) return;
        state="RIDING";pickedAt=System.currentTimeMillis();last=null;waiting=false;waitStartedAt=0;
        saveState();updateBubble();toast("Customer picked • trip fare started");
    }

    private void toggleWaiting() {
        if (!"RIDING".equals(state)) { toast("Tap PICKED CUSTOMER first"); return; }
        long now=System.currentTimeMillis();
        if (!waiting) { waiting=true;waitStartedAt=now;toast("Waiting started"); }
        else { waitAccumMs+=Math.max(0,now-waitStartedAt);waitStartedAt=0;waiting=false;toast("Waiting stopped"); }
        saveState();updateBubble();
    }

    private void endTrip() {
        if ("IDLE".equals(state) || "REVIEW".equals(state)) return;
        if(waiting){waitAccumMs+=Math.max(0,System.currentTimeMillis()-waitStartedAt);waiting=false;waitStartedAt=0;}
        state="REVIEW";stopLocation();last=null;saveState();updateBubble();toast("Ride ended • open RIDER'S PAY for payment");
    }

    private void resetTrip() {
        state="IDLE";waiting=false;pickupMeters=0;rideMeters=0;manualAmount=-1;startedAt=0;pickedAt=0;waitAccumMs=0;waitStartedAt=0;last=null;p.edit().putFloat("manualAmount",-1f).apply();stopLocation();saveState();updateBubble();
    }

    private void dutyOff() {
        p.edit().putBoolean("duty",false).apply();
        stopLocation();
        if(handler!=null&&ticker!=null)handler.removeCallbacks(ticker);
        if(root!=null){try{wm.removeView(root);}catch(Exception ignored){}root=null;} if(qrWeb!=null){try{qrWeb.destroy();}catch(Exception ignored){}qrWeb=null;}
        stopForeground(true);stopSelf();
    }

    private void startLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return;
        try {
            if(lm.isProviderEnabled(LocationManager.GPS_PROVIDER))lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,2,this);
            if(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,2000,5,this);
        } catch(Exception ignored){}
    }
    private void stopLocation(){try{if(lm!=null)lm.removeUpdates(this);}catch(Exception ignored){}}

    @Override public void onLocationChanged(Location loc) {
        if(loc==null||loc.getAccuracy()>60||waiting||"IDLE".equals(state)||"REVIEW".equals(state)) { if(loc!=null&&loc.getAccuracy()<=60)last=loc; return; }
        if(last==null){last=loc;return;}
        float d=last.distanceTo(loc);long dt=Math.max(1,(loc.getTime()-last.getTime())/1000);float implied=d/dt;last=loc;
        if(d<3||d>300||implied>45)return;
        if("PICKUP".equals(state))pickupMeters+=d;else if("RIDING".equals(state))rideMeters+=d;
        saveState();updateBubble();
    }
    @Override public void onProviderEnabled(String p){}
    @Override public void onProviderDisabled(String p){}
    @Override public void onStatusChanged(String p,int s,Bundle b){}

    private long currentWaitMs(){ return waitAccumMs + (waiting&&waitStartedAt>0?Math.max(0,System.currentTimeMillis()-waitStartedAt):0); }
    private boolean nightNow(){int h=java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),a=p.getInt("nightStart",23),b=p.getInt("nightEnd",5);return a==b?false:(a>b?(h>=a||h<b):(h>=a&&h<b));}
    private double baseFare(){
        double km=rideMeters/1000.0,threshold=p.getFloat("longStartKm",10f),rate=km>=threshold?p.getFloat("longRate",12f):p.getFloat("shortRate",9.5f);
        double fare=Math.max(0,km)*rate;
        double minKm=p.getFloat("minFareKm",4.5f), minFare=p.getFloat("minFare",50f);
        if(km>0 && km<minKm) fare=Math.max(fare,minFare);
        return fare;
    }
    private double nightFare(){double b=baseFare();return nightNow()?b*(p.getFloat("nightPercent",30f)/100.0):0;}
    private double pickupFare(){double km=pickupMeters/1000.0;return Math.min(Math.max(0,km-p.getFloat("pickupFreeKm",2f))*p.getFloat("pickupRate",5f),p.getFloat("pickupMax",15f));}
    private int waitMinutes(){return Math.min((int)(currentWaitMs()/60000L),p.getInt("waitMaxMinutes",15));}
    private double waitingFare(){return waitMinutes()*p.getFloat("waitPerMin",1f);}
    private double surgeFare(){return Math.max(0,p.getFloat("defaultSurge",0f));}
    private double totalFare(){return manualAmount>0?manualAmount:baseFare()+nightFare()+pickupFare()+waitingFare()+surgeFare();}

    private void updateBubble() {
        if(bubble==null)return;
        double fare=totalFare(),km=rideMeters/1000.0;
        bubble.setText(String.format(Locale.US,"₹%.0f\n%.1f km",fare,km));
        startBtn.setEnabled("IDLE".equals(state)||"REVIEW".equals(state));
        pickedBtn.setEnabled("PICKUP".equals(state));
        waitBtn.setEnabled("RIDING".equals(state));
        waitBtn.setText(waiting?"WAIT ON":"WAIT");
        amountBtn.setEnabled(!"IDLE".equals(state));
    qrBtn.setEnabled("RIDING".equals(state)||"REVIEW".equals(state));
        startBtn.setAlpha(startBtn.isEnabled()?1f:.45f);pickedBtn.setAlpha(pickedBtn.isEnabled()?1f:.45f);waitBtn.setAlpha(waitBtn.isEnabled()?1f:.45f);amountBtn.setAlpha(amountBtn.isEnabled()?1f:.45f);qrBtn.setAlpha(qrBtn.isEnabled()?1f:.45f);
    }

    private void saveState() {
        p.edit().putBoolean("duty",true).putString("state",state).putBoolean("waiting",waiting)
            .putFloat("pickupMeters",(float)pickupMeters).putFloat("rideMeters",(float)rideMeters)
            .putLong("startedAt",startedAt).putLong("pickedAt",pickedAt).putLong("waitAccumMs",waitAccumMs).putLong("waitStartedAt",waitStartedAt).apply();
    }
    private void loadState() {
        state=p.getString("state","IDLE");waiting=p.getBoolean("waiting",false);pickupMeters=p.getFloat("pickupMeters",0);rideMeters=p.getFloat("rideMeters",0);manualAmount=p.getFloat("manualAmount",-1f);
        startedAt=p.getLong("startedAt",0);pickedAt=p.getLong("pickedAt",0);waitAccumMs=p.getLong("waitAccumMs",0);waitStartedAt=p.getLong("waitStartedAt",0);
        if(waiting&&waitStartedAt==0)waitStartedAt=System.currentTimeMillis();
    }

    public static String snapshotJson(Context c) {
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        try{
            String state=p.getString("state","IDLE");boolean waiting=p.getBoolean("waiting",false);double pickup=p.getFloat("pickupMeters",0)/1000.0,ride=p.getFloat("rideMeters",0)/1000.0;
            long waitMs=p.getLong("waitAccumMs",0)+(waiting&&p.getLong("waitStartedAt",0)>0?Math.max(0,System.currentTimeMillis()-p.getLong("waitStartedAt",0)):0);
            int wm=Math.min((int)(waitMs/60000L),p.getInt("waitMaxMinutes",15));
            double base=ride*(ride>=p.getFloat("longStartKm",10f)?p.getFloat("longRate",12f):p.getFloat("shortRate",9.5f));
            if(ride>0 && ride<p.getFloat("minFareKm",4.5f)) base=Math.max(base,p.getFloat("minFare",50f));
            int h=java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),a=p.getInt("nightStart",23),b=p.getInt("nightEnd",5);
            boolean night=a==b?false:(a>b?(h>=a||h<b):(h>=a&&h<b));
            double nf=night?base*(p.getFloat("nightPercent",30f)/100.0):0;
            double pf=Math.min(Math.max(0,pickup-p.getFloat("pickupFreeKm",2f))*p.getFloat("pickupRate",5f),p.getFloat("pickupMax",15f));
            double wf=wm*p.getFloat("waitPerMin",1f); double sf=Math.max(0,p.getFloat("defaultSurge",0f)); double manual=p.getFloat("manualAmount",-1f);
            JSONObject o=new JSONObject();
            o.put("duty",p.getBoolean("duty",false));o.put("state",state);o.put("waiting",waiting);o.put("pickupKm",pickup);o.put("rideKm",ride);o.put("waitingMinutes",wm);
            o.put("baseFare",base);o.put("nightFare",nf);o.put("pickupFare",pf);o.put("waitingFare",wf);o.put("surge",sf);o.put("manualAmount",manual);o.put("fare",manual>0?manual:base+nf+pf+wf+sf);o.put("startedAt",p.getLong("startedAt",0));o.put("pickedAt",p.getLong("pickedAt",0));
            return o.toString();
        }catch(Exception e){return "{}";}
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    @Override public void onDestroy() {
        if(waiting){waitAccumMs+=Math.max(0,System.currentTimeMillis()-waitStartedAt);waiting=false;waitStartedAt=0;saveState();}
        stopLocation();if(handler!=null&&ticker!=null)handler.removeCallbacks(ticker);if(root!=null){try{wm.removeView(root);}catch(Exception ignored){}root=null;}if(qrWeb!=null){try{qrWeb.destroy();}catch(Exception ignored){}qrWeb=null;}super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){return null;}
}
