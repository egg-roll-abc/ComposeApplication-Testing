from appium import webdriver
from selenium.webdriver.common.by import By
from appium.options.android import UiAutomator2Options
import os
import time
import csv
import re
from datetime import datetime

# ===================== 配置区 =====================
APP_PACKAGE = "com.shx.composeapplication"
APP_ACTIVITY = ".MainActivity"
APPIUM_SERVER = "http://127.0.0.1:4723"
SWIPE_X = 500
SWIPE_START_Y = 1600
SWIPE_END_Y = 400
TEST_DURATION = 60          # 每个场景测试时长(秒)
COLLECT_INTERVAL = 5        # 性能数据采集间隔(秒)
# ==================================================

def init_driver():
    """初始化 Appium 驱动"""
    options = UiAutomator2Options()
    options.platform_name = "Android"
    options.device_name = "Pixel 7 API 34"
    options.app_package = APP_PACKAGE
    options.app_activity = APP_ACTIVITY
    options.automation_name = "UiAutomator2"
    options.no_reset = True
    options.new_command_timeout = 300
    
    driver = webdriver.Remote(APPIUM_SERVER, options=options)
    print("✅ Appium驱动连接成功，已打开应用")
    return driver

def get_app_pid():
    """获取应用进程ID"""
    cmd = f"adb shell pidof -s {APP_PACKAGE}"
    res = os.popen(cmd).read().strip()
    return res if res else "0"

def get_memory_mb(pid):
    """获取内存使用 (MB)"""
    if pid == "0":
        return 0.0
    cmd = f"adb shell dumpsys meminfo {pid}"
    output = os.popen(cmd).read()
    
    match = re.search(r"TOTAL PSS:\s+(\d+)", output)
    if match:
        return int(match.group(1)) / 1024
    
    match = re.search(r"TOTAL\s+(\d+)", output)
    if match:
        return int(match.group(1)) / 1024
    return 0.0

def get_cpu_percent(pid):
    """获取 CPU 使用率 (%)"""
    if pid == "0":
        return 0.0
    
    # 方法1：使用 top 命令，在 Python 中过滤
    cmd = f"adb shell top -n 1 -d 0.3"
    output = os.popen(cmd).read()
    
    if output:
        for line in output.split('\n'):
            if pid in line:
                match = re.search(r'(\d+\.?\d*)%', line)
                if match:
                    return float(match.group(1))
    
    # 方法2：使用 dumpsys cpuinfo
    cmd2 = f"adb shell dumpsys cpuinfo"
    output2 = os.popen(cmd2).read()
    if output2:
        for line in output2.split('\n'):
            if APP_PACKAGE in line:
                match = re.search(r'(\d+\.?\d*)%', line)
                if match:
                    return float(match.group(1))
    return 0.0

def get_fps_info():
    """获取帧率信息"""
    cmd = f"adb shell dumpsys gfxinfo {APP_PACKAGE}"
    output = os.popen(cmd).read()
    
    total_frames = len(re.findall(r"FrameCompleted", output))
    
    jank_count = 0
    framestats_match = re.search(r"---PROFILEDATA---\n(.*?)\n\n", output, re.DOTALL)
    
    if framestats_match:
        lines = framestats_match.group(1).strip().split('\n')
        for line in lines:
            parts = line.split(',')
            if len(parts) >= 14:
                try:
                    intended_vsync = int(parts[0])
                    frame_completed = int(parts[12])
                    frame_time_ms = (frame_completed - intended_vsync) / 1_000_000
                    if 0 < frame_time_ms < 1000 and frame_time_ms > 16.67:
                        jank_count += 1
                except:
                    pass
    
    fps = min(60, int(total_frames / 60 * 60)) if total_frames > 0 else 0
    
    return {
        "fps": fps,
        "total_frames": total_frames,
        "jank_count": jank_count,
        "jank_rate": round(jank_count / total_frames * 100, 2) if total_frames > 0 else 0
    }

def get_battery_info():
    """获取电池信息"""
    try:
        cmd = "adb shell dumpsys battery"
        output = os.popen(cmd).read()
        
        level_match = re.search(r'level: (\d+)', output)
        temp_match = re.search(r'temperature: (\d+)', output)
        
        level = int(level_match.group(1)) if level_match else 0
        temp = int(temp_match.group(1)) / 10 if temp_match else 0
        return {"level": level, "temperature": temp}
    except:
        return {"level": 0, "temperature": 0}

def collect_performance():
    """采集所有性能数据"""
    pid = get_app_pid()
    memory = get_memory_mb(pid)
    cpu = get_cpu_percent(pid)
    fps_info = get_fps_info()
    battery = get_battery_info()
    timestamp = datetime.now().strftime("%H:%M:%S")
    
    data = {
        "timestamp": timestamp,
        "pid": pid,
        "memory_mb": round(memory, 2),
        "cpu_percent": round(cpu, 2),
        "fps": fps_info["fps"],
        "jank_count": fps_info["jank_count"],
        "jank_rate": fps_info["jank_rate"],
        "battery_level": battery["level"],
        "battery_temp": round(battery["temperature"], 1)
    }
    
    cpu_color = "🟢" if cpu < 30 else "🟡" if cpu < 60 else "🔴"
    mem_color = "🟢" if memory < 150 else "🟡" if memory < 250 else "🔴"
    fps_color = "🟢" if fps_info["fps"] >= 55 else "🟡" if fps_info["fps"] >= 30 else "🔴"
    
    print(f"📊 [{timestamp}] {mem_color}内存:{memory:.1f}MB | {cpu_color}CPU:{cpu:.1f}% | {fps_color}FPS:{fps_info['fps']} | Jank率:{fps_info['jank_rate']}% | 🔋电池:{battery['level']}%/{battery['temperature']:.0f}°C")
    return data

def init_csv_log(file_name):
    header = ["timestamp", "pid", "memory_mb", "cpu_percent", "fps", "jank_count", "jank_rate", "battery_level", "battery_temp"]
    with open(file_name, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=header)
        writer.writeheader()

def write_log(file_name, data):
    with open(file_name, "a", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=data.keys())
        writer.writerow(data)

# ===================== 测试场景1：滑动列表 =====================
def test_scroll_list(driver):
    log_file = "01_滑动列表_性能日志.csv"
    init_csv_log(log_file)
    print("\n" + "="*50)
    print("🚀 场景1：列表滑动测试")
    print(f"   时长: {TEST_DURATION}秒 | 采集间隔: {COLLECT_INTERVAL}秒")
    print("="*50)
    
    start_time = time.time()
    last_collect_time = 0
    sample_count = 0
    
    while time.time() - start_time < TEST_DURATION:
        driver.swipe(SWIPE_X, SWIPE_START_Y, SWIPE_X, SWIPE_END_Y, 600)
        time.sleep(0.3)
        
        current_time = time.time() - start_time
        if current_time - last_collect_time >= COLLECT_INTERVAL:
            perf_data = collect_performance()
            write_log(log_file, perf_data)
            last_collect_time = current_time
            sample_count += 1
            print(f"   📍 采样 #{sample_count} | 进度: {current_time:.0f}/{TEST_DURATION}s")
    
    print(f"\n✅ 场景1完成 | 采样点数: {sample_count}")

# ===================== 测试场景2：页面切换 =====================
def test_switch_page(driver):
    log_file = "02_页面切换_性能日志.csv"
    init_csv_log(log_file)
    print("\n" + "="*50)
    print("🚀 场景2：页面切换测试")
    print(f"   时长: {TEST_DURATION}秒 | 采集间隔: {COLLECT_INTERVAL}秒")
    print("="*50)
    
    start_time = time.time()
    last_collect_time = 0
    sample_count = 0
    switch_count = 0
    
    while time.time() - start_time < TEST_DURATION:
        try:
            my_tab = driver.find_element(By.XPATH, "//*[contains(@text,'我的')]")
            my_tab.click()
            time.sleep(0.5)
            switch_count += 1
            
            home_tab = driver.find_element(By.XPATH, "//*[contains(@text,'首页')]")
            home_tab.click()
            time.sleep(0.5)
            switch_count += 1
        except Exception as e:
            print(f"   ⚠️ 切换异常: {e}")
            driver.back()
            time.sleep(1)
        
        current_time = time.time() - start_time
        if current_time - last_collect_time >= COLLECT_INTERVAL:
            perf_data = collect_performance()
            write_log(log_file, perf_data)
            last_collect_time = current_time
            sample_count += 1
            print(f"   📍 采样 #{sample_count} | 切换次数: {switch_count} | 进度: {current_time:.0f}/{TEST_DURATION}s")
    
    print(f"\n✅ 场景2完成 | 切换次数: {switch_count} | 采样点数: {sample_count}")

# ===================== 测试场景3：添加+删除账单 =====================
def test_add_delete_records(driver):
    log_file = "03_增删账单_性能日志.csv"
    init_csv_log(log_file)
    print("\n" + "="*50)
    print("🚀 场景3：添加账单 + 删除账单测试")
    print(f"   时长: {TEST_DURATION}秒 | 采集间隔: {COLLECT_INTERVAL}秒")
    print("="*50)
    
    start_time = time.time()
    last_collect_time = 0
    sample_count = 0
    add_count = 0
    del_count = 0
    
    expense_categories = ["餐饮", "交通", "购物", "居住", "娱乐", "医疗", "教育", "其他"]
    
    while time.time() - start_time < TEST_DURATION:
        try:
            # 步骤1：点击加号
            add_fab = driver.find_element(By.XPATH, "//*[@content-desc='添加' or contains(@content-desc,'记一笔')]")
            add_fab.click()
            time.sleep(0.8)
            
            # 步骤2：选择支出
            expense_tab = driver.find_element(By.XPATH, "//*[contains(@text,'支出')]")
            expense_tab.click()
            time.sleep(0.3)
            
            # 步骤3：输入金额
            amount = (add_count % 9 + 1) * 10
            money_input = driver.find_element(By.XPATH, "//*[contains(@hint,'金额') or contains(@text,'金额')]")
            money_input.click()
            time.sleep(0.3)
            
            # 使用 ADB 输入数字
            os.system(f'adb shell input text "{amount}"')
            time.sleep(0.5)
            
            # 步骤4：选择分类
            category = expense_categories[add_count % len(expense_categories)]
            category_btn = driver.find_element(By.XPATH, f"//*[contains(@text,'{category}')]")
            category_btn.click()
            time.sleep(0.3)
            
            # 步骤5：点击保存
            save_btn = driver.find_element(By.XPATH, "//*[contains(@text,'保存')]")
            save_btn.click()
            time.sleep(0.8)
            add_count += 1
            print(f"   ✅ 添加 #{add_count}: ¥{amount} ({category})")
            
            # 步骤6：删除账单
            trash_icon = driver.find_element(By.XPATH, "//*[contains(@content-desc,'删除')]")
            trash_icon.click()
            time.sleep(0.5)
            
            driver.tap([(777, 1364)])
            time.sleep(0.5)
            del_count += 1
            print(f"   🗑️ 删除 ")
            
        except Exception as e:
            print(f"   ❌ 操作异常: {e}")
            driver.back()
            time.sleep(1)
        
        current_time = time.time() - start_time
        if current_time - last_collect_time >= COLLECT_INTERVAL:
            perf_data = collect_performance()
            write_log(log_file, perf_data)
            last_collect_time = current_time
            sample_count += 1
            print(f"   📊 采样 #{sample_count} | 净增: {add_count - del_count} | 进度: {current_time:.0f}/{TEST_DURATION}s")
    
    print(f"\n✅ 场景3完成 | 添加: {add_count}条 | 删除: {del_count}条 | 采样: {sample_count}个")

# ===================== 测试场景4：空闲监控 =====================
def test_idle_monitor(driver):
    log_file = "04_空闲监控_性能日志.csv"
    init_csv_log(log_file)
    print("\n" + "="*50)
    print("🚀 场景4：空闲状态监控")
    print(f"   时长: {TEST_DURATION}秒 | 采集间隔: {COLLECT_INTERVAL}秒")
    print("="*50)
    
    start_time = time.time()
    last_collect_time = 0
    sample_count = 0
    
    while time.time() - start_time < TEST_DURATION:
        current_time = time.time() - start_time
        if current_time - last_collect_time >= COLLECT_INTERVAL:
            perf_data = collect_performance()
            write_log(log_file, perf_data)
            last_collect_time = current_time
            sample_count += 1
            print(f"   📍 采样 #{sample_count} | 进度: {current_time:.0f}/{TEST_DURATION}s")
        time.sleep(1)
    
    print(f"\n✅ 场景4完成 | 采样点数: {sample_count}")

# ===================== 主函数 =====================
if __name__ == "__main__":
    driver = None
    try:
        print("\n🔧 请确保 Appium 服务已启动 (运行 'appium')")
        input("⏎ 按 Enter 键继续...")
        
        driver = init_driver()
        time.sleep(3)
        
        test_scroll_list(driver)
        test_switch_page(driver)
        test_add_delete_records(driver)
        test_idle_monitor(driver)
        
        print("\n" + "="*60)
        print("🎉 全部测试完成！")
        print("="*60)
        print("\n📁 生成的文件:")
        print("   ├── 01_滑动列表_性能日志.csv")
        print("   ├── 02_页面切换_性能日志.csv")
        print("   ├── 03_增删账单_性能日志.csv")
        print("   └── 04_空闲监控_性能日志.csv")
        print("="*60)
        
    except Exception as err:
        print(f"\n❌ 脚本运行异常: {err}")
        import traceback
        traceback.print_exc()
    finally:
        if driver:
            driver.quit()
        print("\n🔌 Appium 连接已关闭")