package com.zcq.demo;

import cn.hutool.core.io.FileUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.File;

public class TestHutool {
    public static void main(String[] args) {
//        String alias = EmojiUtil.toHtml("😄");//&#128102;
//        System.out.println(alias);
        QrConfig config = new QrConfig(300, 300);
        // 设置边距，既二维码和背景之间的边距
        config.setMargin(3);
        // 设置前景色，既二维码颜色（青色）
//        config.setForeColor(Color.CYAN.getRGB());
        // 设置背景色（灰色）
//        config.setBackColor(Color.GRAY.getRGB());
        config.setErrorCorrection(ErrorCorrectionLevel.L);
        config.setImg("D:\\doc\\my\\studymd\\demo\\icon.jpg");

        File file = FileUtil.file("D:\\doc\\my\\studymd\\demo\\test.jpg");



        // 生成二维码到文件，也可以到流
//        QrCodeUtil.generate("王鑫", config, file);
        String context = "https://image.baidu.com/search/detail?ct=503316480&z=0&ipn=d&word=%E5%B0%8F%E4%B8%91%E8%8B%B1%E9%9B%84%E8%81%94%E7%9B%9F&step_word=&hs=0&pn=0&spn=0&di=7264239678495129601&pi=0&rn=1&tn=baiduimagedetail&is=0%2C0&istype=2&ie=utf-8&oe=utf-8&in=&cl=2&lm=-1&st=-1&cs=1879469459%2C1883053005&os=3790957768%2C1861618725&simid=3468382812%2C285509669&adpicid=0&lpn=0&ln=1461&fr=&fmq=1706681312413_R&fm=result&ic=&s=undefined&hd=&latest=&copyright=&se=&sme=&tab=0&width=&height=&face=undefined&ist=&jit=&cg=&bdtype=0&oriquery=&objurl=https%3A%2F%2Fmedia.9game.cn%2Fgamebase%2F2021%2F6%2F13%2F227434979.png&fromurl=ippr_z2C%24qAzdH3FAzdH3Fooo_z%26e3Bl2w4j_z%26e3BvgAzdH3FgjofAzdH3Fcdal8da_z%26e3Bip4s&gsm=1e&rpstart=0&rpnum=0&islist=&querylist=&nojc=undefined&dyTabStr=MCwxLDMsMiw2LDUsNCw4LDcsOQ%3D%3D&lid=11120795693939625535";

        QrCodeUtil.generate(
                context,
                config,
                FileUtil.file(file)//写出到的文件
        );
    }
}

