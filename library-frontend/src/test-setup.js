// jsdom 未实现 window.scrollTo；vue-router 导航后的滚动行为会调用它，测试中置空以消除噪音。
window.scrollTo = () => {}
