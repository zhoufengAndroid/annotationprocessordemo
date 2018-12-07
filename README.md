##### 仿写BufferKnife来熟悉自定义注解

### 1、创建一个Java Library：annotationprocessor

##### ==注意：一定要创建一个Java Library，而不是Android Library==
因为在使用自定义AbstractProcessor需要使用到javax包中的相关类和接口，这个在android库中并不存在，所以需要使用到Java库。

![image](https://note.youdao.com/yws/public/resource/5aef1ffec3225db39702748e76308294/xmlnote/AC38FD6A68604B63862E4B6F68E18805/9385)

#### build.gradle的配置：

```
apply plugin: 'java'

dependencies {
    implementation fileTree(include: ['*.jar'], dir: 'libs')
    implementation 'com.google.auto.service:auto-service:1.0-rc3'
    implementation 'com.squareup:javapoet:1.9.0'
}

sourceCompatibility = "1.7"
targetCompatibility = "1.7"

```
#### 自定义注解处理器Processor：ZFViewBinderProcessor

通过运行时annotation预处理技术实现动态的生成代码

为了实现编译时生成代码需要配置：\
依赖：

```
implementation 'com.squareup:javapoet:1.9.0'
```
自定义注解处理器注解：@AutoService(Processor.class)
```
@AutoService(Processor.class)
public class ZFViewBinderProcessor extends AbstractProcessor {
    //...
}
```
这样就完成了配置，会生成这样一个文件：

![image](https://note.youdao.com/yws/public/resource/5aef1ffec3225db39702748e76308294/xmlnote/D33C434AF28A4BEC88CAE98456C473A3/9465)


```
@AutoService(Processor.class)
public class ZFViewBinderProcessor extends AbstractProcessor {

    private Filer mFiler; //文件相关的辅助类
    private Elements mElementUtils; //元素相关的辅助类
    private Messager mMessager; //日志相关的辅助类
    private Map<String, AnnotatedClass> mAnnotatedClassMap;

    /**
     * 该方法主要用于一些初始化的操作，通过该方法的参数ProcessingEnvironment可以获取一些列有用的工具类。
     * @param processingEnv
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mFiler = processingEnv.getFiler();
        mElementUtils = processingEnv.getElementUtils();
        mMessager = processingEnv.getMessager();
        mAnnotatedClassMap = new TreeMap<>();
    }

     /**
     * 注解处理器的核心方法，处理具体的注解
     * @param set
     * @param roundEnv
     * @return
     */
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnv) {
        mAnnotatedClassMap.clear();
        try {
            processBindView(roundEnv);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            error(e.getMessage());
        }

        for (AnnotatedClass annotatedClass : mAnnotatedClassMap.values()) {
            try {
                annotatedClass.generateFile().writeTo(mFiler);
            } catch (IOException e) {
                error("Generate file failed, reason: %s", e.getMessage());
            }
        }
        return true;
    }

    private void processBindView(RoundEnvironment roundEnv) throws IllegalArgumentException {

        for (Element element : roundEnv.getElementsAnnotatedWith(BindView.class)) {
            AnnotatedClass annotatedClass = getAnnotatedClass(element);
            BindViewField bindViewField = new BindViewField(element);
            annotatedClass.addField(bindViewField);
        }
    }

    private AnnotatedClass getAnnotatedClass(Element element) {
        TypeElement typeElement = (TypeElement) element.getEnclosingElement();
        String fullName = typeElement.getQualifiedName().toString();
        AnnotatedClass annotatedClass = mAnnotatedClassMap.get(fullName);
        if (annotatedClass == null) {
            annotatedClass = new AnnotatedClass(typeElement, mElementUtils);
            mAnnotatedClassMap.put(fullName, annotatedClass);
        }
        return annotatedClass;
    }

    /**
     * 返回此 Processor 支持的注释类型的名称。
     * @return
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        types.add(BindView.class.getCanonicalName());
        return types;
    }
    private void error(String msg, Object... args) {
        mMessager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args));
    }

    /**
     * 返回此注释 Processor 支持的最新的源版本，该方法可以通过注解@SupportedSourceVersion指定。
     * @return
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
```
#### 注解类：BindView

```
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface BindView {
    int value() default -1;
}
```
#### 预编译时动态生成Java类：AnnotatedClass

```
public class AnnotatedClass {
    private static class TypeUtil {
        static final ClassName BINDER = ClassName.get("com.zf.annotationapi", "ViewBinder");
        static final ClassName PROVIDER = ClassName.get("com.zf.annotationapi", "ViewFinder");
    }

    private TypeElement mTypeElement;
    private ArrayList<BindViewField> mFields;
    private Elements mElements;

    public AnnotatedClass(TypeElement mTypeElement, Elements mElements) {
        this.mTypeElement = mTypeElement;
        this.mFields = new ArrayList<>();
        this.mElements = mElements;
    }

    void addField(BindViewField field) {
        mFields.add(field);
    }

    JavaFile generateFile() {
        //generateMethod
        MethodSpec.Builder bindViewMethod = MethodSpec.methodBuilder("bindView")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(TypeName.get(mTypeElement.asType()), "host")
                .addParameter(TypeName.OBJECT, "source")
                .addParameter(TypeUtil.PROVIDER, "finder");

        for (BindViewField field : mFields) {
            // find views
            bindViewMethod.addStatement("host.$N = ($T)(finder.findView(source, $L))",
                    field.getFieldName(), ClassName.get(field.getFieldType()), field.getResId());
        }

        MethodSpec.Builder unBindViewMethod = MethodSpec.methodBuilder("unBindView")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(TypeName.get(mTypeElement.asType()), "host")
                .addAnnotation(Override.class);
        for (BindViewField field : mFields) {
            unBindViewMethod.addStatement("host.$N = null", field.getFieldName());
        }

        //generaClass
        TypeSpec injectClass = TypeSpec.classBuilder(mTypeElement.getSimpleName() + "$$ViewBinder")
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(TypeUtil.BINDER, TypeName.get(mTypeElement.asType())))
                .addMethod(bindViewMethod.build())
                .addMethod(unBindViewMethod.build())
                .build();

        String packageName = mElements.getPackageOf(mTypeElement).getQualifiedName().toString();

        return JavaFile.builder(packageName, injectClass).build();
    }

}
```
#### 每个绑定项的元素信息类：BindViewField

```
class BindViewField {
    private VariableElement mVariableElement;
    private int mResId;

    BindViewField(Element element) throws IllegalArgumentException {
        if (element.getKind() != ElementKind.FIELD) {
            throw new IllegalArgumentException(String.format("Only fields can be annotated with @%s",
                    BindView.class.getSimpleName()));
        }
        mVariableElement = (VariableElement) element;

        BindView bindView = mVariableElement.getAnnotation(BindView.class);
        mResId = bindView.value();
        if (mResId < 0) {
            throw new IllegalArgumentException(
                    String.format("value() in %s for field %s is not valid !", BindView.class.getSimpleName(),
                            mVariableElement.getSimpleName()));
        }
    }

    /**
     * 获取变量名称
     *
     * @return
     */
    Name getFieldName() {
        return mVariableElement.getSimpleName();
    }

    /**
     * 获取变量id
     *
     * @return
     */
    int getResId() {
        return mResId;
    }

    /**
     * 获取变量类型
     *
     * @return
     */
    TypeMirror getFieldType() {
        return mVariableElement.asType();
    }
}
```
### 2、创建一个Android Library:annotationapi

```
public class ActivityViewFinder implements ViewFinder {
    @Override
    public View findView(Object object, int id) {
        return ((Activity) object).findViewById(id);
    }
}
```

```
public class ZFViewBinder {
    private static final ActivityViewFinder activityFinder = new ActivityViewFinder();//默认声明一个Activity查找器
    private static final Map<String, ViewBinder> binderMap = new LinkedHashMap<>();//

    /**
     * Activity的注解绑定
     *
     * @param activity
     */
    public static void bind(Activity activity) {
        bind(activity, activity, activityFinder);
    }

    /**
     * '注解绑定
     *
     * @param host   表示注解 View 变量所在的类，也就是注解类
     * @param object 表示查找 View 的地方，Activity & View 自身就可以查找，Fragment 需要在自己的 itemView 中查找
     * @param finder ui绑定提供者接口
     */
    public static void bind(Object host, Object object, ViewFinder finder) {
        try {
            ViewBinder binder = findViewBinderForClass(host.getClass());
            if (binder != null) {
                binder.bindView(host, object, finder);
            }
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static ViewBinder<Object> findViewBinderForClass(Class<?> cls)
            throws IllegalAccessException, InstantiationException {
        if (cls == null) return null;
        String clsName = cls.getName();
        ViewBinder<Object> viewBinder = binderMap.get(clsName);
        if (viewBinder != null) {
            return viewBinder;
        }
        try {
            Class<?> viewBindingClass = Class.forName(clsName + "$$ViewBinder");
            viewBinder = (ViewBinder<Object>) viewBindingClass.newInstance();
        } catch (ClassNotFoundException e) {
            viewBinder = findViewBinderForClass(cls.getSuperclass());
        }
        binderMap.put(clsName, viewBinder);
        return viewBinder;
    }

    public static void unBind(Object host) {
        String className = host.getClass().getName();
        ViewBinder binder = binderMap.get(className);
        if (binder != null) {
            binder.unBindView(host);
        }
        binderMap.remove(className);
    }
}
```

```
public interface ViewFinder {
    View findView(Object object, int id);
}
```

```
public interface ViewBinder<T> {
    void bindView(T host, Object object, ViewFinder finder);

    void unBindView(T host);
}
```
### 3、在主项目的Activity中使用
gradle中引用：

```
implementation project(':annotationapi')
implementation project(':annotationprocessor')
annotationProcessor project(':annotationprocessor')
```
Activity中使用：

```
public class MainActivity extends AppCompatActivity {

    @BindView(R.id.tv)
    TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ZFViewBinder.bind(this);
        if (tv!=null){
            tv.setText("绑定成功");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ZFViewBinder.unBind(this);
    }
}
```












