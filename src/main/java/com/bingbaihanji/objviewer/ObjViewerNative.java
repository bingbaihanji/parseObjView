package com.bingbaihanji.objviewer;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ObjViewerNative extends Application {

    private static final double WIDTH = 1024;
    private static final double HEIGHT = WIDTH * 0.618;

    // 缩放、旋转、平移控制
    private final Scale modelScale = new Scale(1, 1, 1); // 控制模型的缩放
    private double anchorX, anchorY; // 鼠标初始位置
    private double anchorAngleX = 0, anchorAngleY = 0; // 初始旋转角度
    private final Rotate rotateX = new Rotate(0, Rotate.X_AXIS); // X轴旋转
    private final Rotate rotateY = new Rotate(0, Rotate.Y_AXIS); // Y轴旋转
    private final Translate modelTranslate = new Translate(0, 0, 0); // 模型平移
    // 3D视图对象
    private MeshView meshView;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {

        // 主布局面板
        AnchorPane anchorPane = new AnchorPane();
        anchorPane.setStyle("-fx-background-color: #00000000"); // 透明背景

        BorderPane root = new BorderPane();


        LinearGradient linearGradient = new LinearGradient(
                0.0, 0.0, 1.0, 0.0, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, new Color(0.99, 0.55, 1.0, 1.0)),
                new Stop(0.5, new Color(0.17, 0.83, 0.99, 1.0)),
                new Stop(1.0, new Color(0.18, 1.0, 0.54, 1.0))

        );

        MenuBar menuBar = new MenuBar();

        menuBar.setBackground(new Background(new BackgroundFill(linearGradient, null, null)));
        Menu file = new Menu("文件");
        Menu tools = new Menu("工具");
        MenuItem importObj = new MenuItem("导入obj文件");
        MenuItem importMapFile = new MenuItem("导入UV贴图文件(漫反射贴图)");
        MenuItem importBumpMapFile = new MenuItem("导入材质法线贴图文件");
        MenuItem screenshots = new MenuItem("截图");


        file.getItems().addAll(importObj, importMapFile, importBumpMapFile);
        tools.getItems().addAll(screenshots);
        menuBar.getMenus().addAll(file, tools);

        root.setTop(menuBar);


        // 添加光源并设定其位置
        ColorPicker colorPicker = new ColorPicker();
        colorPicker.getStyleClass().addAll(ColorPicker.STYLE_CLASS_SPLIT_BUTTON);

        HBox hBox = new HBox(10.0);

        hBox.prefWidthProperty().bind(primaryStage.widthProperty().multiply(0.2));
        Label label = new Label("选择光照颜色");
        label.setTextFill(Color.MEDIUMPURPLE);
        hBox.getChildren().addAll(label, colorPicker);

        PointLight light = new PointLight(Color.WHITE);
        colorPicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            // 光照颜色
            light.setColor(Color.valueOf(newValue.toString()));
        });

        light.setTranslateX(WIDTH / 2.0);
        light.setTranslateY(HEIGHT / 2.0);
        light.setTranslateZ(-500);

        VBox vBox = new VBox(10.0);
        vBox.setBackground(new Background(new BackgroundFill(Color.valueOf("#EF9BF299"), null, null)));


        ToggleButton aSwitch = getSwitch(anchorPane, light);
        Label label2 = new Label("是否开启光照");
        label2.setTextFill(Color.MEDIUMPURPLE);
        HBox hBox1 = new HBox(10.0);
        hBox1.prefWidthProperty().bind(primaryStage.widthProperty().multiply(0.2));
        hBox1.getChildren().addAll(label2, aSwitch);

        Label label3 = new Label("设置镜面反射颜色");
        label3.setTextFill(Color.MEDIUMPURPLE);
        ColorPicker colorPicker2 = new ColorPicker();
        colorPicker2.getStyleClass().addAll(ColorPicker.STYLE_CLASS_SPLIT_BUTTON);
        colorPicker2.valueProperty().addListener((observable, oldValue, newValue) -> {
            PhongMaterial material = (PhongMaterial) meshView.getMaterial();
            material.setSpecularColor(Color.valueOf(newValue.toString())); // 设置镜面反射颜色
        });
        HBox hBox2 = new HBox(10);
        hBox2.prefWidthProperty().bind(primaryStage.widthProperty().multiply(0.2));
        hBox2.getChildren().addAll(label3, colorPicker2);


        Label label4 = new Label("设置漫反射颜色");
        label4.setTextFill(Color.MEDIUMPURPLE);
        ColorPicker colorPicker3 = new ColorPicker();
        colorPicker3.getStyleClass().addAll(ColorPicker.STYLE_CLASS_SPLIT_BUTTON);
        colorPicker3.valueProperty().addListener((observable, oldValue, newValue) -> {
            PhongMaterial material = (PhongMaterial) meshView.getMaterial();
            material.setDiffuseColor(Color.valueOf(newValue.toString())); // 置漫反射反射颜色
        });
        HBox hBox3 = new HBox(10);
        hBox3.prefWidthProperty().bind(primaryStage.widthProperty().multiply(0.2));
        hBox3.getChildren().addAll(label4, colorPicker3);


        vBox.getChildren().addAll(hBox, hBox1, hBox2, hBox3);
        root.setLeft(vBox);

        // 设置透视摄像机
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-10); // 设置摄像机距离
        camera.setNearClip(0.1);
        camera.setFarClip(3000); // 远裁剪平面


        // 创建场景，并启用抗锯齿效果
        SubScene subScene = new SubScene(anchorPane, WIDTH, HEIGHT, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.PALETURQUOISE); // 设置背景色
        subScene.setCamera(camera); // 设置摄像机
        subScene.heightProperty().bind(root.heightProperty());
        subScene.widthProperty().bind(root.widthProperty().subtract(hBox.widthProperty())); // 控制3D模型居中


        root.setCenter(subScene);
        // 初始化鼠标拖拽和滚轮事件（旋转与缩放）
        initMouseDrag(subScene);
        // 初始化键盘事件（平移模型）


        Scene scene = new Scene(root);
        initKeyPress(scene, modelTranslate);
        primaryStage.setTitle("JavaFX 3D Viewer");

        primaryStage.setWidth(WIDTH);
        primaryStage.setHeight(HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.show();

        // 导入3D模型
        importObj.setOnAction(event -> {

            FileChooser fileChooser = new FileChooser();
            FileChooser.ExtensionFilter extensionFilter = new FileChooser.ExtensionFilter("Obj File", ".obj", "*.obj");
            fileChooser.getExtensionFilters().add(extensionFilter);
            File obj = fileChooser.showOpenDialog(primaryStage);
            if (obj != null) {
                meshView = loadObjFile(obj);
                if (meshView != null) {
                    // 设置模型材质
                    PhongMaterial material = new PhongMaterial();

                    // material.setDiffuseColor(Color.WHITESMOKE); // 设置漫反射颜色
                    // material.setSpecularColor(Color.WHITE); // 设置镜面反射颜色
                    meshView.setMaterial(material);


                    // 应用缩放、旋转、平移等转换操作
                    meshView.getTransforms().addAll(rotateX, rotateY, modelTranslate, modelScale);

                    // 将3D模型和光源添加到场景中
                    anchorPane.getChildren().clear();
                    anchorPane.getChildren().addAll(light, meshView);
                }
            }
        });

        // 导入UV贴图文件(漫反射贴图)
        importMapFile.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            FileChooser.ExtensionFilter png = new FileChooser.ExtensionFilter("Map File", ".png", "*.png");
            FileChooser.ExtensionFilter jpg = new FileChooser.ExtensionFilter("Map File", ".jpg", "*.jpg");
            fileChooser.getExtensionFilters().addAll(png, jpg);
            File map = fileChooser.showOpenDialog(primaryStage);
            if (map != null) {
                PhongMaterial material = (PhongMaterial) meshView.getMaterial();
                // 漫反射UV贴图
                material.setDiffuseMap(new Image(String.valueOf(map)));
            }


        });
        // 导入材质法线贴图
        importBumpMapFile.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            FileChooser.ExtensionFilter png = new FileChooser.ExtensionFilter("Map File", ".png", "*.png");
            FileChooser.ExtensionFilter jpg = new FileChooser.ExtensionFilter("Map File", ".jpg", "*.jpg");
            fileChooser.getExtensionFilters().addAll(png, jpg);
            File bumMap = fileChooser.showOpenDialog(primaryStage);
            if (bumMap != null) {
                PhongMaterial material = (PhongMaterial) meshView.getMaterial();
                // 漫反射UV贴图
                material.setBumpMap(new Image(String.valueOf(bumMap)));
            }

        });


        // 截图
        screenshots.setOnAction(event -> {
            // 创建快照
            SnapshotParameters snapshotParameters = new SnapshotParameters();
            snapshotParameters.setFill(Color.TRANSPARENT); // 使用透明背景
            WritableImage image = subScene.snapshot(snapshotParameters, null);

            // 保存截图到剪切板
            Clipboard systemClipboard = Clipboard.getSystemClipboard(); // 获取系统剪切板
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putImage(image);
            systemClipboard.setContent(clipboardContent);

            // 保存到文件
            BufferedImage png = SwingFXUtils.fromFXImage(image, null);
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Image File", ".png", "*.png")
            );
            File save = fileChooser.showSaveDialog(primaryStage);
            if (save != null) {
                try {
                    ImageIO.write(png, "png", save);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        });

    }

    // 解析OBJ文件
    private MeshView loadObjFile(File file) {
        List<Float> vertices = new ArrayList<>(); // 顶点列表
        List<Integer> faces = new ArrayList<>(); // 面数据列表
        List<Float> textureCoords = new ArrayList<>(); // UV坐标列表

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("v ")) {
                    // 顶点行 "v x y z"
                    String[] tokens = line.split("\\s+");
                    vertices.add(Float.parseFloat(tokens[1]));
                    vertices.add(Float.parseFloat(tokens[2]));
                    vertices.add(Float.parseFloat(tokens[3]));
                } else if (line.startsWith("vt ")) {
                    // UV坐标行 "vt u v"
                    String[] tokens = line.split("\\s+");
                    textureCoords.add(Float.parseFloat(tokens[1]));
                    textureCoords.add(-Float.parseFloat(tokens[2]));
                } else if (line.startsWith("f ")) {
                    // 面行 "f v1/vt1 v2/vt2 v3/vt3"
                    String[] tokens = line.split("\\s+");
                    for (int i = 1; i <= 3; i++) {
                        String[] vertexData = tokens[i].split("/");
                        int vertexIndex = Integer.parseInt(vertexData[0]) - 1; // 顶点索引
                        int textureIndex = Integer.parseInt(vertexData[1]) - 1; // UV索引
                        faces.add(vertexIndex);
                        faces.add(textureIndex);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return createMeshView(vertices, faces, textureCoords); // 将顶点和面数据转为MeshView
    }

    private MeshView createMeshView(List<Float> vertices, List<Integer> faces, List<Float> textureCoords) {
        TriangleMesh mesh = new TriangleMesh();

        // 添加顶点
        float[] points = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            points[i] = vertices.get(i);
        }
        mesh.getPoints().addAll(points);

        // 添加纹理坐标
        float[] texCoords = new float[textureCoords.size()];
        for (int i = 0; i < textureCoords.size(); i++) {
            texCoords[i] = textureCoords.get(i);
        }
        mesh.getTexCoords().addAll(texCoords);

        // 添加面
        int[] faceArray = new int[faces.size()];
        for (int i = 0; i < faces.size(); i++) {
            faceArray[i] = faces.get(i);
        }
        mesh.getFaces().addAll(faceArray);

        return new MeshView(mesh); // 返回MeshView
    }

    // 初始化鼠标拖动与缩放事件处理
    private void initMouseDrag(SubScene scene) {
        scene.addEventFilter(MouseEvent.ANY, event -> {
            if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
                anchorX = event.getSceneX();
                anchorY = event.getSceneY();
                anchorAngleX = rotateX.getAngle();
                anchorAngleY = rotateY.getAngle();
            } else if (event.getEventType() == MouseEvent.MOUSE_DRAGGED) {
                double deltaX = event.getSceneX() - anchorX;
                double deltaY = event.getSceneY() - anchorY;
                rotateX.setAngle(anchorAngleX + (deltaY / 3)); // 调整X轴旋转
                rotateY.setAngle(anchorAngleY + (deltaX / 3)); // 调整Y轴旋转
            }
        });

        // 设置滚轮缩放事件
        scene.setOnScroll((ScrollEvent event) -> {
            double delta = event.getDeltaY();
            double scaleFactor = (delta > 0) ? 1.1 : 0.9; // 根据滚动方向缩放
            modelScale.setX(modelScale.getX() * scaleFactor);
            modelScale.setY(modelScale.getY() * scaleFactor);
            modelScale.setZ(modelScale.getZ() * scaleFactor);
        });
    }

    // 初始化键盘事件处理（平移模型）
    private void initKeyPress(Scene scene, Translate modelTranslate) {
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case W: // 向上平移
                    modelTranslate.setY(modelTranslate.getY() - 1);
                    break;
                case S: // 向下平移
                    modelTranslate.setY(modelTranslate.getY() + 1);
                    break;
                case A: // 向左平移
                    modelTranslate.setX(modelTranslate.getX() - 1);
                    break;
                case D: // 向右平移
                    modelTranslate.setX(modelTranslate.getX() + 1);
                    break;
                default:
                    break;
            }
        });


    }

    public ToggleButton getSwitch(AnchorPane root, PointLight pointLight) {
        ToggleButton toggleButton = new ToggleButton("光照开");
        // 初始状态：设置为选中状态（光源开启）
        toggleButton.setSelected(true);
        root.getChildren().add(pointLight);
        toggleButton.setStyle(
                "-fx-background-color: #4caf50;" +    // 绿色背景
                        "-fx-text-fill: white;" +             // 白色文字
                        "-fx-effect: innershadow(gaussian, rgba(0, 255, 0, 0.8), 20, 0.3, 0, 0), " + // 内阴影
                        "dropshadow(gaussian, rgba(0, 255, 0, 0.8), 20, 0.3, 0, 0);"  // 发光效果
        );

        toggleButton.setOnAction(event -> {
            if (toggleButton.isSelected()) {
                toggleButton.setText("光照开");
                // 开启状态样式：发光效果
                toggleButton.setStyle(
                        "-fx-background-color: #4caf50;" +    // 绿色背景
                                "-fx-text-fill: white;" +             // 白色文字
                                "-fx-effect: innershadow(gaussian, rgba(0, 255, 0, 0.8), 20, 0.3, 0, 0), " + // 内阴影
                                "dropshadow(gaussian, rgba(0, 255, 0, 0.8), 20, 0.3, 0, 0);"  // 发光效果
                );
                root.getChildren().remove(pointLight);
                root.getChildren().add(pointLight);

            } else {
                toggleButton.setText("光照关");
                // 关闭状态样式：暗阴影
                toggleButton.setStyle(
                        "-fx-background-color: #f44336;" +    // 红色背景
                                "-fx-text-fill: white;" +             // 白色文字
                                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.8), 15, 0.4, 0, 0);" // 较暗的阴影
                );
                root.getChildren().remove(pointLight);

            }
        });

        return toggleButton;
    }

}
