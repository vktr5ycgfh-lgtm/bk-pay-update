window.BK_PAY_UPDATE = {
  versionCode: 104,
  versionName: "1.7.1",
  forceUpdate: true,
  apkUrl: "https://raw.githubusercontent.com/vktr5ycgfh-lgtm/bk-pay-update/main/BK-PAY-latest.apk",
  message: "BK PAY v1.7.1 adds cloud OTP accounts, cloud payment-history backup, duplicate cleanup, profile controls and separate voice language.",
  noticeId: "bkpay-v171-cloud",
  noticeTitle: "BK PAY v1.7.1 cloud update",
  noticeMessage: "Cloud account, payment-history sync, duplicate cleanup, profile and voice-language controls are ready."
};
if (window.BKPayApplyUpdate) {
  window.BKPayApplyUpdate(window.BK_PAY_UPDATE);
}
