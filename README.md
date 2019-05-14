# module_api
## gradle实现Android组件化开发模块api化

最近看了[微信Android模块化架构重构实践](https://mp.weixin.qq.com/s/6Q818XA5FaHd7jJMFBG60w)这篇文章，刚好自己又正在搭建新项目的框架，用到组件化开发；感觉文章里面的一些技巧很有用，就跟着实现了一下，写一下自己的看法

### 模块间的交互
首先是解决模块之前的依赖问题，模块间肯定是不能相互依赖的，那如何进行数据交互就是一个问题了；比如用户模块和其他模块，其他模块如何在不依赖用户模块的情况下获取到用户信息；


#### 使用EventBus
想要获取用户信息，那User类肯定是要引用的，肯定是要提取出User类放到公共模块里面，然后获取User可以通过EventBus来获取数据

公共模块将EventBus发送的Event定义为接口
```java
public interface UserCallback {

    /**
     * 获取用户数据
     *
     * @param user
     */
    void getUser(User user);
}
```
然后在用户模块订阅事件，返回用户信息
```java
    @Subscribe
    public void getUser(UserCallback callback){
        callback.getUser(new com.dhht.baselibrary.User());
    }
```
在其他模块就可以通过EventBus来发送事件获取到用户信息
```java
EventBus.getDefault().post(new UserCallback() {
    @Override
    public void getUser(User user) {
        mUser = user;
    }
});
```
但是讲道理EventBus还是少用的好，业务多了会生成很多Event类，感觉是有点难受的，而且代码阅读起来非常难；

#### SPI机制
SPI全称Service Provider Interface，是Java提供的一套用来被第三方实现或者扩展的API，它可以用来启用框架扩展和替换组件。

整体机制图如下：

![image](http://upload-images.jianshu.io/upload_images/4906791-317d275c4d2260ae.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)


##### 具体的实现（可以略过）
首先也是把User放在公共模块里面，获取用户信息的接口也放在公共模块里面
```java
package com.dhht.baselibrary;
public interface UserService {
    /**
     * 获取user
     *
     * @return
     */
    User getUser();
}

```
然后在用户模块里面实现接口
```java
package com.dhht.user;

public class UserImpl implements UserService {
    @Override
    public User getUser() {
        return new User("UserImpl");
    }
}
```

需要在`user/src/main/resources/META-INF.services/`目录下面新建文件名为`com.dhht.baselibrary.UserService`的文件，文件内容就是实现类的路径
```java
com.dhht.user.UserImpl
```

这个时候再其他模块使用这个实现类就可以通过SPI机制来获取
```java
        ServiceLoader<UserService> userServices = ServiceLoader.load(UserService.class);
        Iterator<UserService> iterator = userServices.iterator();
        while (iterator.hasNext()) {
            UserService userService = iterator.next();
            ToastUtil.showShort(userService.getUser().getName());
        }
```

#### ARouter
上面的过程稍微有点复杂，也没必要去实现，这个是一种思想，很多路由框架都是借助了这种思想，而且使用非常方便，比如阿里的ARouter框架；用户类不变，接口需要实现**IProvider**接口

```java
public interface UserService extends IProvider {
    UserInfo getUser();
}
```

然后在用户模块实现接口，并且添加`@Route`注解
```java
@Route(path = "/user/UserService")
public class UserServiceImpl implements UserService {
    @Override
    public UserInfo getUser() {
        return new UserInfo("Tyhj");
    }

    @Override
    public void init(Context context) {

    }
}
```
然后在其他模块通过ARouter注解获取实例
```java
    @Autowired//(name = "/user/UserService")
    UserService mUserService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ARouter.getInstance().inject(this);
        ...
```
方法比较简单，相对于正常的代码只是添加了一个注解而已，ARouter的最新版本如下，每个模块都需要添加注解插件（第二行），库（第一行）只需要在公共模块添加就好了；
```
//arouter
api 'com.alibaba:arouter-api:1.4.1'
annotationProcessor 'com.alibaba:arouter-compiler:1.2.2'
```

使用ARouter还需要在每个模块的build.gradle的defaultConfig节点下添加如下代码

```java
 javaCompileOptions {
            annotationProcessorOptions {
                arguments = [AROUTER_MODULE_NAME: project.getName()]
            }
        }
```

#### 提取出api模块
如果每次有一个模块要使用另一个模块的接口都把接口和相关文件放到公共模块里面，那么公共模块会越来越大，而且每个模块都依赖了公共模块，都依赖了一大堆可能不需要的东西；

所以我们可以提取出每个模块提供api的文件放到各种单独的模块里面；比如user模块，我们把公共模块里面的User和UserInfoService放到新的user-api模块里面，这样其他模块使用的时候可以单独依赖于这个专门提供接口的模块，以此解决公共模块膨胀的问题

#### 自动生成Library
为了写代码方便，我们可以在写代码的时候，每个模块的东西都写在一起，比如User提供的接口我们也正常写在用户模块里面，在编译的时候，再使用gradle来自动生成各个api模块，这样会方便很多


原理是这样的，我们把需要单独生成api模块的.java文件改为另一种文件类型比如把UserInfo.java改为UserInfo.api，在设置/Editor/File Type中找到Java类型，添加*.api，然后就可以和Java文件一样使用了；

在项目的setting.gradle文件里面添加方法`includeWithApi("module名字")`，用这个方法来代替`include ":module名字"`，这个方法会从这个module里面找到以.api结尾的文件，复制到新的module里面并重命名，当然也会复制`gradle`文件和`AndroidManifest`文件，以此生成新的api模块

##### 具体实现
setting.gradle文件的实现
```java
def includeWithApi(String moduleName) {
    //先正常加载这个模块
    include(moduleName)
    //找到这个模块的路径
    String originDir = project(moduleName).projectDir
    //这个是新的路径
    String targetDir = "${originDir}-api"
    //原模块的名字
    String originName=project(moduleName).name;
    //新模块的名字
    def sdkName = "${originName}-api"

    //todo 替换成自己的公共模块，或者预先放api.gradle的模块
    //这个是公共模块的位置，我预先放了一个 新建的api.gradle 文件进去
    String apiGradle = project(":baselibrary").projectDir

    // 每次编译删除之前的文件
    deleteDir(targetDir)

    //复制.api文件到新的路径
    copy() {
        from originDir
        into targetDir
        exclude '**/build/'
        exclude '**/res/'
        include '**/*.api'
    }


    //直接复制公共模块的AndroidManifest文件到新的路径，作为该模块的文件
    copy() {
        from "${apiGradle}/src/main/AndroidManifest.xml"
        into "${targetDir}/src/main/"
    }

    //复制 gradle文件到新的路径，作为该模块的gradle
    copy() {
        from "${apiGradle}/api.gradle"
        into "${targetDir}/"
    }

    //删除空文件夹
    deleteEmptyDir(new File(targetDir))


    //todo 替换成自己的包名，这里是 com/dhht/
    //为AndroidManifest新建路径，路径就是在原来的包下面新建一个api包，作为AndroidManifest里面的包名
    String packagePath = "${targetDir}/src/main/java/com/dhht/${originName}/api";


    //todo 替换成自己的包名，这里是baselibrary模块拷贝的AndroidManifest，替换里面的包名
    //修改AndroidManifest文件包路径
    fileReader("${targetDir}/src/main/AndroidManifest.xml", "commonlibrary","${originName}.api");

    new File(packagePath).mkdirs()

    //重命名一下gradle
    def build = new File(targetDir + "/api.gradle")
    if (build.exists()) {
        build.renameTo(new File(targetDir + "/build.gradle"))
    }

    // 重命名.api文件，生成正常的.java文件
    renameApiFiles(targetDir, '.api', '.java')

    //正常加载新的模块
    include ":$sdkName"
}

private void deleteEmptyDir(File dir) {
    if (dir.isDirectory()) {
        File[] fs = dir.listFiles();
        if (fs != null && fs.length > 0) {
            for (int i = 0; i < fs.length; i++) {
                File tmpFile = fs[i];
                if (tmpFile.isDirectory()) {
                    deleteEmptyDir(tmpFile);
                }
                if (tmpFile.isDirectory() && tmpFile.listFiles().length <= 0) {
                    tmpFile.delete();
                }
            }
        }
        if (dir.isDirectory() && dir.listFiles().length == 0) {
            dir.delete();
        }
    }
}

private void deleteDir(String targetDir) {
    FileTree targetFiles = fileTree(targetDir)
    targetFiles.exclude "*.iml"
    targetFiles.each { File file ->
        file.delete()
    }
}

/**
 * rename api files(java, kotlin...)
 */
private def renameApiFiles(root_dir, String suffix, String replace) {
    FileTree files = fileTree(root_dir).include("**/*$suffix")
    files.each {
        File file ->
            file.renameTo(new File(file.absolutePath.replace(suffix, replace)))
    }
}

//替换AndroidManifest里面的字段
def fileReader(path, name,sdkName) {
    def readerString = "";
    def hasReplace = false

    file(path).withReader('UTF-8') { reader ->
        reader.eachLine {
            if (it.find(name)) {
                it = it.replace(name, sdkName)
                hasReplace = true
            }
            readerString <<= it
            readerString << '\n'
        }

        if (hasReplace) {
            file(path).withWriter('UTF-8') {
                within ->
                    within.append(readerString)
            }
        }
        return readerString
    }
}


include ':app', ':baselibrary'
includeWithApi ":user"
includeWithApi ":other"

```

其实讲的还是比较清楚了，我首先复制.api文件去生成Java文件，想要生成新的api模块，得有`gradle`和`AndroidManifest`文件才行，而这个api模块显然不需要过多的配置，于是我自己先生成一个简单的`gradle`文件，就是其他模块复制过来的，基础配置而已，然后复制到新的api模块搞定，对于AndroidManifest文件，基础模块肯定是没有什么配置的，复制过来使用完事儿；


### AndroidManifest路径问题
下面这个demo是随便写的，不是按照组件化来写的，只是简单展示一下这个脚本的作用而已，[组件化框架搭建点这里](https://blog.csdn.net/guiying712/article/details/55213884)，第一个版本写完后能运行没发现问题，但是有位兄弟发现build的时候居然失败了，报错如下：
```java
AGPBI: {"kind":"error","text":"Program type already present: com.dhht.commonlibrary.BuildConfig","sources":[{}],"tool":"D8"}
```
这个错误很常见，意思就是`com.dhht.commonlibrary.BuildConfig`这个文件重复了，明显是因为我直接拷贝**AndroidManifest**文件，里面的包名没有修改导致的
```java
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.dhht.commonlibrary" >
```

发现只要将`minSdkVersion`设置为`21`就可以避免这个问题
```java
defaultConfig {
        minSdkVersion 21
        ...
```
但是后来打包签名apk的又报错了，那其实我们随便设置不同的包名就可以了，但是包名不能设置不存在的路径，所以在新的模块的原包下新建一个api文件夹，然后在复制过来的**AndroidManifest**里面修改包名，也不用把`minSdkVersion`设置为21，当然都是脚本完成
```java
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.dhht.user.api">
```

我这里创建的是Android Library，其实创建Java Library也是一样的，只是我感觉Android Library更好一点；可能感觉稍微有点复杂，其实只需要编写一个通用的setting.gradle文件然后改改.java文件名而已，这个也是微信重构的一个技巧，我觉得还是挺好的


> 项目地址：https://github.com/tyhjh/module_api
