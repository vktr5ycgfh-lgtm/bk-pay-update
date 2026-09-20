window.BK_PAY_UPDATE = {
  versionCode: 104,
  versionName: "1.7.1",
  forceUpdate: false,
  apkUrl: "https://raw.githubusercontent.com/vktr5ycgfh-lgtm/bk-pay-update/main/BK-PAY-latest.apk",
  message: "Mobile OTP delivery needs the server SMS provider. The forced v1.7.1 update has been paused while the OTP hotfix is being rolled out.",
  noticeId: "bkpay-v171-otp-pause",
  noticeTitle: "Mobile OTP update notice",
  noticeMessage: "Forced update is paused. Mobile OTP needs an active SMS provider; use the OTP hotfix/offline mode until SMS delivery is configured."
};
if (window.BKPayApplyUpdate) {
  window.BKPayApplyUpdate(window.BK_PAY_UPDATE);
}
