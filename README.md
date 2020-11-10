这个项目来源于https://github.com/googlearchive/android-Camera2Raw


其中，我只是修改了部分代码，实现了延时摄影功能。目前还处于开发阶段，测试我的华为P9手机的摄像头功能和查看照片元数据信息。


其中我把DNG文件保存改成了保存1920×1080像素的16位拜尔图像元数据raw文件，以此减少手机硬件存储压力。以此来测试手机拍摄每张图片到保存的最少间隔！


16位拜尔源图像需要在PC端使用Python项目PyDNG把其转换为DNG文件，以供后期调色使用。


我的PyDNG项目raw2dng.py实现把raw转成DNG文件，项目链接https://github.com/Liaojunquan/bayerRaw2DNG


现在支持JPEG、RAW、DNG、DNG+JPEG格式保存，JPEG可修改图片尺寸，其它格式均不能修改。RAW默认1920×1080。


我添加了两种模式：延时摄影模式和极限模式。延时摄影模式即间隔定时摄影。极限模式是尽相机设备最快的速度去拍摄保存每一张图片，显示x.xxfps帧速率，以此可以查看手机相机的性能。
