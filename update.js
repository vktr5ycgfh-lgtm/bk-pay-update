window.BK_PAY_UPDATE = {
  versionCode: 6,
  versionName: "1.5",
  forceUpdate: false,
  apkUrl: "https://raw.githubusercontent.com/vktr5ycgfh-lgtm/bk-pay-update/main/BK-PAY-latest.apk",
  message: "BK PAY is up to date.",
  noticeId: "bkpay-v17-settings",
  noticeTitle: "BK PAY account & settings",
  noticeMessage: "The new BK PAY build adds sign in/sign up, voice controls, language and theme settings, custom UPI ID, notification bell and Help Bot."
};
if (window.BKPayApplyUpdate) {
  window.BKPayApplyUpdate(window.BK_PAY_UPDATE);
}
