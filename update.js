window.BK_PAY_UPDATE = {
  versionCode: 111,
  versionName: "1.1",
  forceUpdate: true,
  apkUrl: "https://raw.githubusercontent.com/vktr5ycgfh-lgtm/bk-pay-update/main/BK-PAY-latest.apk",
  message: "RIDER'S PAY v1.1 is required. Adds Duty ON/OFF and a floating live fare bubble with Start Ride, Picked Customer and Waiting controls while Maps or other apps are open.",
  noticeId: "riders-pay-v11-required",
  noticeTitle: "RIDER'S PAY v1.1 update required",
  noticeMessage: "Duty mode and floating fare controls are now available. Turn Duty ON, allow Display over other apps and Location, then use the floating fare bubble during rides."
};
if (window.BKPayApplyUpdate) {
  window.BKPayApplyUpdate(window.BK_PAY_UPDATE);
}
