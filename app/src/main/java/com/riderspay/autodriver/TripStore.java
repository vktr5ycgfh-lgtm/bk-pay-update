package com.riderspay.autodriver;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Local, offline-first ride ledger; no automatic payment-verification claims. */
public final class TripStore {
    private static final String KEY = "trip_history_v1";
    private final SharedPreferences prefs;

    public TripStore(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public JSONArray all() {
        try { return new JSONArray(prefs.getString(KEY, "[]")); }
        catch (JSONException ignored) { return new JSONArray(); }
    }

    public void add(String id, long started, long ended, double meters, long amountPaise) throws JSONException {
        JSONObject trip = new JSONObject();
        trip.put("id", id);
        trip.put("started", started);
        trip.put("ended", ended);
        trip.put("meters", Math.round(meters));
        trip.put("amountPaise", amountPaise);
        trip.put("paid", false);
        trip.put("paymentMethod", "PENDING");
        JSONArray previous = all();
        JSONArray updated = new JSONArray();
        updated.put(trip);
        for (int i = 0; i < previous.length(); i++) updated.put(previous.get(i));
        // commit() reports disk persistence before showing a payment QR.
        if (!prefs.edit().putString(KEY, updated.toString()).commit()) {
            throw new JSONException("Could not save ride locally");
        }
    }

    public boolean markPaid(String id, String method) {
        try {
            JSONArray rides = all();
            for (int i = 0; i < rides.length(); i++) {
                JSONObject ride = rides.getJSONObject(i);
                if (id.equals(ride.optString("id"))) {
                    ride.put("paid", true);
                    ride.put("paymentMethod", method);
                    ride.put("paymentStatus", "MANUALLY_RECORDED_NOT_VERIFIED");
                    ride.put("paidAt", System.currentTimeMillis());
                    return prefs.edit().putString(KEY, rides.toString()).commit();
                }
            }
        } catch (JSONException ignored) { }
        return false;
    }
}
