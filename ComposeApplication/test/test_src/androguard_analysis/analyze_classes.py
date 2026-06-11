import os
import sys

# 添加项目根目录到 Python 路径（如果需要）
PROJECT_ROOT = r"D:\Android\finalhomework\ComposeApplication-Testing\ComposeApplication"

def is_system_class(class_name):
    """判断是否为系统类（非自定义类）"""
    system_prefixes = [
        'android.', 'androidx.', 'com.android.', 'com.google.android.',
        'kotlin.', 'kotlinx.', 'java.', 'javax.', 'sun.', 'jdk.',
        'org.apache.', 'org.json.', 'org.xml.', 'org.w3c.', 'org.intellij.', 'org.jetbrains.',
        'dagger.', 'com.squareup.', 'com.google.gson.', 'com.google.protobuf.',
        'okhttp3.', 'okio.', 'retrofit2.', 'rx.', 'io.reactivex.',
    ]

    for prefix in system_prefixes:
        if class_name.startswith(prefix):
            return True
    return False


def analyze_custom_classes(apk_path, output_dir):
    """分析 APK 中的自定义类"""

    # 动态导入 androguard（确保已安装）
    try:
        from androguard.core.apk import APK
        from androguard.misc import AnalyzeAPK
    except ImportError:
        print("错误：未找到 androguard 模块")
        print("请在 Anaconda Prompt 中执行: pip install androguard")
        return None

    print("=" * 70)
    print(" Androguard 自定义类统计分析")
    print(f" 分析时间: {__import__('datetime').datetime.now()}")
    print(f" APK路径: {apk_path}")
    print("=" * 70)

    # 1. 检查文件是否存在
    if not os.path.exists(apk_path):
        print(f"\n[错误] APK文件不存在: {apk_path}")
        return None

    # 2. 加载 APK
    print("\n[1] 正在加载 APK...")
    try:
        a = APK(apk_path)
        print(f"    包名: {a.get_package()}")
        print(f"    应用名: {a.get_app_name()}")
        print(f"    APK大小: {os.path.getsize(apk_path) / 1024:.2f} KB")
    except Exception as e:
        print(f"    [错误] 加载失败: {e}")
        return None

    # 3. 分析 DEX 文件
    print("\n[2] 正在分析 DEX 文件...")
    try:
        a_obj, d_obj, dx_obj = AnalyzeAPK(apk_path)
    except Exception as e:
        print(f"    [错误] DEX分析失败: {e}")
        return None

    # 4. 获取所有类
    all_classes = list(dx_obj.get_classes())
    print(f"    DEX中总类数量: {len(all_classes)}")

    # 5. 分类统计
    system_classes = []
    custom_classes = []

    for cls in all_classes:
        class_name = cls.name

        # 清理类名格式（将 Lxxx; 转换为 xxx）
        if class_name.startswith('L') and class_name.endswith(';'):
            class_name = class_name[1:-1]
        display_name = class_name.replace('/', '.')

        if is_system_class(display_name):
            system_classes.append(display_name)
        elif display_name and len(display_name) > 0:
            custom_classes.append(display_name)

    # 6. 输出统计结果
    print("\n" + "=" * 70)
    print(" [3] 统计结果")
    print("=" * 70)
    print(f" 总类数量: {len(all_classes)}")
    print(f" ├── 系统类数量: {len(system_classes)}")
    print(f" ├── 自定义类数量: {len(custom_classes)}")

    if len(all_classes) > 0:
        custom_ratio = len(custom_classes) / len(all_classes) * 100
        print(f" └── 自定义类占比: {custom_ratio:.1f}%")

    # 7. 显示部分自定义类
    print("\n[4] 自定义类示例（前30个）:")
    print("-" * 50)
    for i, cls in enumerate(custom_classes[:30], 1):
        print(f"  {i:2d}. {cls}")

    if len(custom_classes) > 30:
        print(f"  ... 还有 {len(custom_classes) - 30} 个")

    # 8. 按包名分组统计
    print("\n[5] 按包名分组统计（自定义类）:")
    print("-" * 50)

    package_count = {}
    for cls in custom_classes:
        parts = cls.split('.')
        if len(parts) >= 2:
            top_pkg = '.'.join(parts[:2])  # 取前两级包名
        elif len(parts) >= 1:
            top_pkg = parts[0]
        else:
            top_pkg = 'default'
        package_count[top_pkg] = package_count.get(top_pkg, 0) + 1

    for pkg, count in sorted(package_count.items(), key=lambda x: x[1], reverse=True)[:15]:
        print(f"  {pkg}: {count} 个类")

    # 9. 创建输出目录并保存结果
    os.makedirs(output_dir, exist_ok=True)

    custom_file = os.path.join(output_dir, "custom_classes.txt")
    all_file = os.path.join(output_dir, "all_classes.txt")

    with open(custom_file, 'w', encoding='utf-8') as f:
        f.write(f"# 自定义类列表\n")
        f.write(f"# 生成时间: {__import__('datetime').datetime.now()}\n")
        f.write(f"# 总计: {len(custom_classes)} 个自定义类\n")
        f.write("-" * 50 + "\n")
        for cls in custom_classes:
            f.write(cls + "\n")

    with open(all_file, 'w', encoding='utf-8') as f:
        f.write(f"# 全部分类列表\n")
        f.write(f"# 姓名：[你的姓名] 学号：[你的学号]\n")
        f.write(f"# 生成时间: {__import__('datetime').datetime.now()}\n")
        f.write(f"# 总计: {len(all_classes)} 个类\n")
        f.write("-" * 50 + "\n")
        for cls in custom_classes:
            f.write(cls + "\n")

    print(f"\n[6] 结果已保存到: {output_dir}")
    print(f"    - custom_classes.txt: 自定义类列表")
    print(f"    - all_classes.txt: 所有类列表")

    print("\n" + "=" * 70)
    print(" 分析完成！")
    print("=" * 70)

    return {
        'total': len(all_classes),
        'custom': len(custom_classes),
        'system': len(system_classes),
        'custom_ratio': custom_ratio if len(all_classes) > 0 else 0,
        'custom_list': custom_classes
    }


def main():
    # 设置路径
    project_root = r"D:\Android\finalhomework\ComposeApplication-Testing\ComposeApplication"

    # APK 路径
    apk_path = os.path.join(project_root, "app", "build", "outputs", "apk", "debug", "app-debug.apk")

    # 输出目录（test_src/你的姓名/androguard_analysis/output）
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_dir = os.path.join(script_dir, "output")

    print(f"项目根目录: {project_root}")
    print(f"APK路径: {apk_path}")
    print(f"输出目录: {output_dir}")

    if not os.path.exists(apk_path):
        print("\n[错误] 找不到 APK 文件！")
        print("请在 Android Studio 中: Build -> Build APK")
        return

    result = analyze_custom_classes(apk_path, output_dir)

    if result:
        print(f"\n分析结果摘要:")
        print(f"  - 总类数: {result['total']}")
        print(f"  - 自定义类: {result['custom']}")
        print(f"  - 系统类: {result['system']}")
        print(f"  - 自定义类占比: {result['custom_ratio']:.1f}%")


if __name__ == "__main__":
    main()