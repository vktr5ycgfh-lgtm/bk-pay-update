(function(){
  const meta={
  "versionCode": 114,
  "versionName": "1.1.3 Public",
  "forceUpdate": false,
  "graceHours": 24,
  "releaseChannel": "public",
  "apkUrl": "https://raw.githubusercontent.com/vktr5ycgfh-lgtm/bk-pay-update/main/BK-PAY-latest.apk",
  "message": "RIDER'S PAY v1.1.3 is now public. New: floating rider controls, QR PAY at drop, live distance fare, waiting/night/long-pickup calculations, smoother payment flow, notification bell and update checks. Update during the 24-hour grace period.",
  "noticeId": "riders-pay-v113-public-launch-actions",
  "noticeTitle": "RIDER'S PAY v1.1.3 — Update available",
  "noticeMessage": "Tap UPDATE NOW to install the latest public RIDER'S PAY. You can also tap CHECK FOR NEW UPDATES anytime from this notification or Settings."
};
  window.BK_PAY_UPDATE=meta;

  function rpCurrentCode(){
    try{return Number(BK_CURRENT_VERSION_CODE);}catch(_){return NaN;}
  }
  function rpSafe(v){
    try{return typeof esc==='function'?esc(v):String(v).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
    catch(_){return String(v||'');}
  }

  window.ridersPayUpdateNow=function(){
    const rc=Number(meta.versionCode), cc=rpCurrentCode();
    if(Number.isFinite(cc)&&Number.isFinite(rc)&&rc<=cc){
      alert("RIDER'S PAY "+(meta.versionName||'latest')+" is already installed.");
      return;
    }
    try{bkUpdateInfo=meta;}catch(_){}
    try{
      if(typeof bkInstallUpdate==='function'){bkInstallUpdate();return;}
    }catch(_){}
    window.location.href=meta.apkUrl;
  };

  window.ridersPayCheckNow=function(){
    try{
      if(typeof bkCheckUpdates==='function'){bkCheckUpdates(true);return;}
    }catch(_){}
    alert("Unable to start the update check. Please try again.");
  };

  const previousOpen=window.openNotifications;
  window.openNotifications=function(){
    try{
      const a=(typeof getNotifications==='function'?getNotifications():[]);
      const list=document.getElementById('notificationList');
      const cc=rpCurrentCode(), rc=Number(meta.versionCode);
      const updateAvailable=Number.isFinite(cc)&&Number.isFinite(rc)&&rc>cc;
      const cards=a.length?a.map(function(n){
        const id=String(n.id||''), title=String(n.title||'');
        const isUpdate=id.indexOf('update-')===0||/update|launch/i.test(id+' '+title);
        const actions=isUpdate?(
          '<div style="display:grid;grid-template-columns:1fr;gap:8px;margin-top:12px;">'+
          (updateAvailable?'<button onclick="ridersPayUpdateNow()" style="min-height:42px;border:0;border-radius:13px;background:#facc15;color:#111827;font-weight:900;font-size:12px;letter-spacing:.04em;">UPDATE NOW</button>':
          '<button disabled style="min-height:42px;border:1px solid rgba(250,204,21,.45);border-radius:13px;background:rgba(250,204,21,.12);color:#facc15;font-weight:900;font-size:12px;">PUBLIC VERSION ACTIVE</button>')+
          '<button onclick="ridersPayCheckNow()" style="min-height:42px;border:1px solid rgba(148,163,184,.35);border-radius:13px;background:rgba(15,23,42,.7);color:#e2e8f0;font-weight:900;font-size:12px;letter-spacing:.03em;">CHECK FOR NEW UPDATES</button>'+
          '</div>'
        ):'';
        return '<div class="bk-notice"><div class="font-black text-sm">'+rpSafe(n.title)+'</div>'+
          '<div class="text-xs text-zinc-400 mt-1">'+rpSafe(n.message)+'</div>'+
          '<div class="text-[10px] text-zinc-500 mt-2">'+rpSafe(n.time)+'</div>'+actions+'</div>';
      }).join(''):'<p class="text-center text-zinc-500 py-8">No notifications yet.</p>';

      if(list){
        list.innerHTML=cards+
          '<div style="padding-top:4px;"><button onclick="ridersPayCheckNow()" class="bk-soft-btn w-full">CHECK FOR NEW UPDATES</button></div>';
      }
      a.forEach(function(n){n.read=true;});
      if(typeof putNotifications==='function')putNotifications(a);
      const modal=document.getElementById('notificationModal');
      if(modal)modal.classList.add('show');
    }catch(e){
      if(typeof previousOpen==='function')return previousOpen();
    }
  };

  window.BKPayApplyUpdate=function(updateMeta){
    try{
      if(typeof captureUpdateNotice==='function')captureUpdateNotice(updateMeta);
      const remoteCode=Number(updateMeta&&updateMeta.versionCode), currentCode=rpCurrentCode();
      if(!Number.isFinite(remoteCode))throw new Error('Invalid update metadata');
      if(Number.isFinite(currentCode)&&remoteCode<=currentCode){
        if(typeof bkManualUpdateCheck!=='undefined'&&bkManualUpdateCheck){
          alert("RIDER'S PAY "+(updateMeta.versionName||'latest')+" is up to date.");
        }
        try{bkManualUpdateCheck=false;}catch(_){}
        return;
      }
      try{bkUpdateInfo=updateMeta;}catch(_){}
      const v=document.getElementById('bkUpdateVersion');
      const m=document.getElementById('bkUpdateMessage');
      const later=document.getElementById('bkUpdateLater');
      const gate=document.getElementById('bkUpdateGate');
      if(v)v.textContent='Version '+(updateMeta.versionName||remoteCode)+' available';
      if(m)m.textContent=updateMeta.message||"A newer RIDER'S PAY version is available.";
      if(later)later.style.display=updateMeta.forceUpdate?'none':'block';
      if(gate)gate.classList.add('show');
      try{bkManualUpdateCheck=false;}catch(_){}
    }catch(_){
      try{if(bkManualUpdateCheck)alert('Unable to read the update information. Please try again.');bkManualUpdateCheck=false;}catch(__){}
    }
  };

  window.BKPayApplyUpdate(meta);
})();
