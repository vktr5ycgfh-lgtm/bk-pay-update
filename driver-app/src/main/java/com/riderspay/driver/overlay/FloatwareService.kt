package com.riderspay.driver.overlay

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import com.google.firebase.firestore.FirebaseFirestore
import com.riderspay.driver.MainActivity
import com.riderspay.driver.R
import com.riderspay.driver.data.DriverApi
import com.riderspay.driver.data.Ride
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.math.*

class FloatwareService : Service() {
    companion object { const val SHOW = "floatware.SHOW"; const val HIDE = "floatware.HIDE"; private const val YELLOW = 0xFFFFCC00.toInt() }
    private lateinit var wm: WindowManager
    private lateinit var prefs: android.content.SharedPreferences
    private var bubble: View? = null; private var arcLayer: FrameLayout? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var speed: TextView; private lateinit var distance: TextView; private lateinit var fare: TextView
    private val handler = Handler(Looper.getMainLooper()); private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val api = DriverApi(); private var ride: Ride? = null; private var observedRideId: String? = null; private var rideRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var downX = 0; private var downY = 0; private var rawX = 0f; private var rawY = 0f; private var moved = false; private var longPressed = false
    private val hold = Runnable { if (!moved) { longPressed = true; showArc(); bubble?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) } }
    private val tick = object : Runnable { override fun run() { updateHud(); automate(); handler.postDelayed(this, 1_000) } }

    override fun onCreate() {
        super.onCreate(); wm = getSystemService(WindowManager::class.java); prefs = getSharedPreferences("driver_live", MODE_PRIVATE)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("floatware", "Floating driver meter", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(2201, Notification.Builder(this, "floatware").setSmallIcon(R.drawable.ic_auto)
            .setContentTitle("RIDERS PAY floatware").setContentText("Long-press the yellow HUD for ride controls")
            .setContentIntent(open).setOngoing(true).build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == HIDE) { stopSelf(); return START_NOT_STICKY }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        if (bubble == null) createBubble(); observeRide(); handler.post(tick)
        return START_STICKY
    }

    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(w, h,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT)
    private fun dp(v: Float) = (v * resources.displayMetrics.density).roundToInt()
    private fun bg(color: Int, radius: Float, stroke: Int = Color.TRANSPARENT, sw: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (sw > 0) setStroke(dp(sw.toFloat()), stroke)
    }
    private fun text(value: String, sp: Float) = TextView(this).apply {
        setText(value); textSize = sp; setTextColor(0xFF0C0E11.toInt()); gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.MONOSPACE; setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun createBubble() {
        val root = FrameLayout(this).apply { setPadding(dp(4f), dp(4f), dp(4f), dp(4f)); background = bg(Color.BLACK, 20f, 0xFFFFE786.toInt(), 1); elevation = dp(14f).toFloat() }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = bg(YELLOW, 17f); setPadding(dp(3f), dp(2f), dp(3f), dp(2f)) }
        speed = text("0", 12f); body.addView(speed, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        distance = text("0.0\nkm", 7.5f); fare = text("₹0\nfare", 7.5f)
        bottom.addView(distance, LinearLayout.LayoutParams(0, -1, 1f)); bottom.addView(fare, LinearLayout.LayoutParams(0, -1, 1f)); body.addView(bottom, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(body, FrameLayout.LayoutParams(-1, -1))
        bubbleParams = overlayParams(dp(72f), dp(72f)).apply { gravity = Gravity.TOP or Gravity.START; x = prefs.getInt("float_x", dp(12f)); y = prefs.getInt("float_y", dp(180f)) }
        root.setOnTouchListener(::touch); wm.addView(root, bubbleParams); bubble = root
    }

    private fun touch(v: View, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { rawX=e.rawX; rawY=e.rawY; downX=bubbleParams.x; downY=bubbleParams.y; moved=false; longPressed=false; handler.postDelayed(hold, 420); return true }
            MotionEvent.ACTION_MOVE -> { val dx=e.rawX-rawX; val dy=e.rawY-rawY; if (abs(dx)>dp(6f)||abs(dy)>dp(6f)){moved=true;handler.removeCallbacks(hold)}
                bubbleParams.x=(downX+dx).roundToInt().coerceIn(0, resources.displayMetrics.widthPixels-bubbleParams.width)
                bubbleParams.y=(downY+dy).roundToInt().coerceIn(0, resources.displayMetrics.heightPixels-bubbleParams.height); wm.updateViewLayout(v,bubbleParams); hideArc(); return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(hold); prefs.edit().putInt("float_x",bubbleParams.x).putInt("float_y",bubbleParams.y).apply(); if(!moved&&!longPressed) openApp(false); return true }
        }; return false
    }

    private fun showArc() {
        hideArc(); val sw=resources.displayMetrics.widthPixels; val sh=resources.displayMetrics.heightPixels
        val cx=bubbleParams.x+bubbleParams.width/2f; val cy=bubbleParams.y+bubbleParams.height/2f
        val inward=atan2(sh/2f-cy, sw/2f-cx); val radius=dp(112f).toFloat(); val span=Math.toRadians(145.0).toFloat(); val start=inward-span/2
        val layer=FrameLayout(this); layer.addView(ArcView(this,cx,cy,radius,start,span),FrameLayout.LayoutParams(-1,-1))
        val labels=arrayOf("▶\nStart","⚑\nArrived","●\nPicked","⌛\nWaiting","⚐\nDropped","▣\nFare QR")
        val actions=arrayOf("START","ARRIVED","PICKED","WAITING","DROPPED","FARE_QR")
        labels.indices.forEach { i -> val angle=start+span*i/(labels.size-1); val size=dp(52f)
            val node=TextView(this).apply { text=labels[i]; textSize=8.5f; setTextColor(Color.WHITE); gravity=Gravity.CENTER; typeface=android.graphics.Typeface.DEFAULT_BOLD; background=bg(0xEE1C1F24.toInt(),26f,YELLOW,1); elevation=dp(9f).toFloat(); setOnClickListener { action(actions[i]); hideArc() } }
            val px=(cx+radius*cos(angle)-size/2).roundToInt().coerceIn(0,sw-size); val py=(cy+radius*sin(angle)-size/2).roundToInt().coerceIn(0,sh-size)
            layer.addView(node,FrameLayout.LayoutParams(size,size).apply{leftMargin=px;topMargin=py}) }
        val p=overlayParams(-1,-1).apply{gravity=Gravity.TOP or Gravity.START}; wm.addView(layer,p); arcLayer=layer
    }

    private fun action(name: String) {
        if(name=="START") { prefs.edit().putFloat("trip_meters",0f).putLong("trip_started",System.currentTimeMillis()).apply(); Toast.makeText(this,"Meter started",Toast.LENGTH_SHORT).show(); return }
        if(name=="FARE_QR") { openApp(true); return }
        val rideId=prefs.getString("active_ride_id",null) ?: return Toast.makeText(this,"No assigned ride",Toast.LENGTH_SHORT).show()
        scope.launch { runCatching { api.transition(rideId,name) }.onSuccess { if(name=="DROPPED")openApp(true) }.onFailure { Toast.makeText(this@FloatwareService,it.message ?: "Action failed",Toast.LENGTH_SHORT).show() } }
    }

    private fun observeRide() {
        rideRegistration?.remove(); val id=prefs.getString("active_ride_id",null); observedRideId=id; if(id==null){ride=null;return}
        rideRegistration=FirebaseFirestore.getInstance().collection("rides").document(id).addSnapshotListener { s,_ -> ride=s?.toObject(Ride::class.java)?.copy(rideId=id) }
    }
    private fun automate() {
        val r=ride ?: return; val lat=Double.fromBits(prefs.getLong("lat",0)); val lng=Double.fromBits(prefs.getLong("lng",0)); if(lat==0.0&&lng==0.0)return
        val speedKph=prefs.getFloat("speed_mps",0f)*3.6
        when { r.status=="ASSIGNED" && meters(lat,lng,r.pickup.lat,r.pickup.lng)<25 -> auto(r,"ARRIVED")
            r.status=="ARRIVED" && speedKph>8 -> auto(r,"PICKED")
            r.status=="ON_TRIP" && meters(lat,lng,r.drop.lat,r.drop.lng)<30 -> auto(r,"DROPPED") }
    }
    private fun auto(r:Ride,a:String){ val key="auto_${r.rideId}_$a"; if(prefs.getBoolean(key,false))return; scope.launch{runCatching{api.transition(r.rideId,a)}.onSuccess{prefs.edit().putBoolean(key,true).apply();if(a=="DROPPED")openApp(true)}} }
    private fun meters(a:Double,b:Double,c:Double,d:Double):Double{val earth=6371000.0;val p1=Math.toRadians(a);val p2=Math.toRadians(c);val x=Math.toRadians(c-a);val y=Math.toRadians(d-b);val h=sin(x/2).pow(2)+cos(p1)*cos(p2)*sin(y/2).pow(2);return earth*2*atan2(sqrt(h),sqrt(1-h))}
    private fun updateHud(){if(observedRideId!=prefs.getString("active_ride_id",null))observeRide();val m=prefs.getFloat("trip_meters",0f);val paise=max(4000.0,4000.0+m/1000.0*1500.0);speed.text=String.format(Locale.US,"%.0f\nkm/h",prefs.getFloat("speed_mps",0f)*3.6);distance.text=String.format(Locale.US,"%.1f\nkm",m/1000);fare.text=String.format(Locale.US,"₹%.0f\nfare",paise/100)}
    private fun openApp(qr:Boolean){startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("show_fare_qr",qr))}
    private fun hideArc(){arcLayer?.let{runCatching{wm.removeView(it)}};arcLayer=null}
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);rideRegistration?.remove();scope.cancel();hideArc();bubble?.let{runCatching{wm.removeView(it)}};bubble=null;super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null

    private inner class ArcView(c:Context,val cx:Float,val cy:Float,val radius:Float,val start:Float,val span:Float):View(c){
        private val glass=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=dp(58f).toFloat();strokeCap=Paint.Cap.ROUND;color=0x6FE7EEF6;setShadowLayer(dp(18f).toFloat(),0f,0f,0x88FFCC00.toInt())}
        override fun onDraw(canvas:Canvas){super.onDraw(canvas);val o=RectF(cx-radius,cy-radius,cx+radius,cy+radius);canvas.drawArc(o,Math.toDegrees(start.toDouble()).toFloat(),Math.toDegrees(span.toDouble()).toFloat(),false,glass);val edge=Paint(glass).apply{clearShadowLayer();strokeWidth=dp(2f).toFloat();color=0xCFFFFFFF.toInt()};canvas.drawArc(o,Math.toDegrees(start.toDouble()).toFloat(),Math.toDegrees(span.toDouble()).toFloat(),false,edge)}
    }
}
