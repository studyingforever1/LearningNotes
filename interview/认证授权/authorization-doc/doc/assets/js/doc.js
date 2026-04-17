$(function () {
    // 默认隐藏所有子菜单
    // $(".submenu").hide();
    // 使第一个子菜单默认展开
    $(".submenu").first().show();
    // 默认选中第一个菜单的第一个子菜单项
    $(".submenu").first().find("a").first().addClass("active");
    // 监听菜单项的点击事件
    $("ul li a").click(function (event) {
        // 阻止链接的默认行为
        event.preventDefault();
        // 阻止事件冒泡
        event.stopPropagation();
        // 移除所有菜单项的active类
        $("ul li a").removeClass("active");
        // 为当前点击的菜单项添加active类
        $(this).addClass("active");
        // 如果是顶层菜单项，则切换子菜单的显示状态
        // if ($(this).next(".submenu").length > 0) {
        //     $(this).next(".submenu").slideToggle("fast");
        //     $(".submenu").not($(this).next()).slideUp("fast");
        // }
        var newSrc = $(this).attr('href')
        $('iframe').attr('src', newSrc)
    });
    $("ul li").first().find("a").first().click();

    // 确保iframe完全加载后执行
    $('iframe').on('load', function(){
        // 访问iframe内部的document对象
        var iframeBody = $(this).contents();

        // 为iframe内的所有a标签绑定点击事件
        iframeBody.find('a').on('click', function(e){
            // 阻止默认的链接跳转行为
            e.preventDefault();
            // 获取链接的href属性值
            var href = $(this).attr('href');
            // 在新窗口打开链接
            window.open(href, '_blank');
        });
    });
});