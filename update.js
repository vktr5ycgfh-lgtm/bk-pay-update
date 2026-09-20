window.BK_PAY_UPDATE = {
  versionCode: 6,
  versionName: "1.5",
  forceUpdate: false,
  apkUrl: "https://raw.githubusercontent.com/vktr5ycgfh-lgtm/bk-pay-update/main/BK-PAY-latest.apk",
  message: "BK PAY is up to date."
};
if (window.BKPayApplyUpdate) {
  window.BKPayApplyUpdate(window.BK_PAY_UPDATE);
}
