# MotionEmulator

<img src="art/MotionEmulator.svg" width="200">

[English Version](README.md) | 中文文档

Motion Emulator是个模拟连续定位和传感器变化的Xposed模块

## 为啥要用

如果你是不幸的中国大学生，或许体验过 _校园跑_ 

尽管教职工总希望我们在夕阳下跑阳光长跑，我想做些有创造性的事情来让生活更轻松一点

## 咋用

* 打开运动记录器
* 攥紧手机，搞个100m速跑，跑完别忘了按`停`
* 在地图上画个圈儿，表示你想要在那儿模拟跑，或许绕中国大陆画是个好主意
* 设置好速度和圈数
* Make more time

### 加盐
现在，每次模拟，跑的路径都是完全相同的

要让它不那么可疑

* 打开`管理`页面，找到那个路径
* `新建`一个`随机因子`，比如`x`
* 加点盐，比如`旋转`
* 展开你刚加的盐，然后开始敲键盘，加入一些你喜欢点数学表达式，并设法让其中含有`x`

便条：可能需要一点线性代数的知识

### 对于目标位置在中国大陆的用户

由于地图测绘的限制，请在使用一个路径前，先去
管理 -> 路径 页面将该路径的**坐标系统**改为**GCJ02**

好奇的话，参阅
[Restrictions on geographic data in China](https://en.wikipedia.org/wiki/Restrictions_on_geographic_data_in_China)

## 快照版

如果你无聊，可以试试一些新东西，比如我在[这儿](https://build.zhufucdev.com/job/Motion%20Emulator/)
自建的一个快照编译器

我得说，这里面很多都是调试版，它们的签名和正式版不一样。如果你想安装这些 调试版，需要先卸载正式版

## 构建指南

如果你是开发者，请使用最新的Android Studio金丝雀版（当前是Hedgehog | 2023.1.1 Canary 14）构建和维护这个项目，
因为这个项目十分的激进

项目使用了高德地图和Google Maps的API，你得申请些自己的，网址在[这儿](https://console.amap.com/dev/key/app)和
[这儿](https://developers.google.com/maps/documentation/android-sdk/start)

申请完别忘了做些事情
```shell
echo amap.web.key="<Your Key>" >> local.properties
echo AMAP_SDK_KEY="<Your Key>" >> local.properties
echo GCP_MAPS_KEY="<Your Key>" >> local.properties
```

我的私有服务被用来提供一些在线特性，例如自检查更新。这些服务是可选的，并且不会被包括在
第三方构建中。

但还是可以用你自己的服务来构建的
```shell
cd app
echo SERVER_URI="<Your Server>" >> server.properties
echo PRODUCT_NAME="<You Decide>" >> server.properties

cd ../mock_location_plugin
echo SERVER_URI="<Your Server>" >> server.properties
echo PRODUCT_NAME="<You Decide>" >> server.properties
```

`SERVER_URI`是一个HTTP/HTTPS的RESTful API，它实现了特定的一些协议。你可以通过
[查看我的代码库](https://github.com/zhufucdev/api.zhufucdev)来了解这是一个怎样的协议。

顺便说一句，万一你不熟悉Android开发，你要把自己的SDK填进去，就像这样：
```shell
echo sdk.dir=<Your SDK Full Path> >> local.properties
```

## 营业执照

```
Copyright 2022-2023 zhufucdev

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 特别鸣谢

- [wandergis/coordtransform](https://github.com/wandergis/coordtransform) 的地图坐标系转化算法