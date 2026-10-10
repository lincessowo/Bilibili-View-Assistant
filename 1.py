# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
import time
import random
import requests
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from webdriver_manager.chrome import ChromeDriverManager

# 随机User-Agent列表（只包含PC浏览器，不包含手机和Linux UA）
USER_AGENTS = [
    # Chrome浏览器
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
    
    # Edge浏览器
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 Edg/122.0.0.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36 Edg/121.0.0.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 Edg/122.0.0.0",
    
    # QQ浏览器
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36 QQBrowser/10.7.4310.400",
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36 QQBrowser/10.5.2185.400",
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.25 Safari/537.36 Core/1.70.3880.400 QQBrowser/10.8.4554.400",
    
    # 360浏览器
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36 360SE",
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36 360SE",
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36 360SE",
    
    # 2345浏览器
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36 2345Explorer/10.23.0.23018",
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.25 Safari/537.36 Core/1.70.3880.400 QQBrowser/10.8.4554.400 2345Explorer/10.2.0.23018",
    
    # 搜狗浏览器
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36 SE 2.X MetaSr 1.0",
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36 SE 2.X MetaSr 1.0",
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36 SE 2.X MetaSr 1.0"
]

def get_random_ua():
    """获取随机User-Agent"""
    return random.choice(USER_AGENTS)

def get_normal_options():
    """创建正常显示浏览器的选项"""
    chrome_options = Options()
    
    # 正常显示浏览器（不添加无头模式参数）
    
    # 无痕模式
    chrome_options.add_argument("--incognito")
    
    # 允许自动播放
    chrome_options.add_argument("--autoplay-policy=no-user-gesture-required")
    
    # 防检测设置
    chrome_options.add_argument("--disable-blink-features=AutomationControlled")
    chrome_options.add_experimental_option("excludeSwitches", ["enable-automation"])
    chrome_options.add_experimental_option("useAutomationExtension", False)
    
    # 其他优化设置
    chrome_options.add_argument("--disable-extensions")
    chrome_options.add_argument("--disable-popup-blocking")
    chrome_options.add_argument("--disable-notifications")
    
    # 设置窗口大小和位置
    width = random.randint(1200, 1400)
    height = random.randint(800, 1000)
    chrome_options.add_argument(f"--window-size={width},{height}")
    
    # 随机窗口位置
    x_pos = random.randint(0, 200)
    y_pos = random.randint(0, 100)
    chrome_options.add_argument(f"--window-position={x_pos},{y_pos}")
    
    # 随机User-Agent
    chrome_options.add_argument(f"user-agent={get_random_ua()}")
    
    return chrome_options

def clear_browser_data(driver):
    """清除浏览器数据"""
    try:
        # 清除cookies
        driver.delete_all_cookies()
        # 清除本地存储
        driver.execute_script("window.localStorage.clear();")
        # 清除会话存储
        driver.execute_script("window.sessionStorage.clear();")
        print("浏览器数据已清除")
    except Exception as e:
        print(f"清除浏览器数据时出错: {e}")

def get_video_views():
    """通过API获取视频播放量"""
    try:
        # API地址
        api_url = "https://api.bilibili.com/x/web-interface/view?aid=114753305447594"
        
        # 设置请求头
        headers = {
            "User-Agent": get_random_ua(),
            "Referer": "https://www.bilibili.com",
            "Accept": "application/json, text/plain, */*"
        }
        
        # 发送请求
        response = requests.get(api_url, headers=headers, timeout=10)
        response.raise_for_status()
        
        data = response.json()
        
        if data["code"] == 0:
            view_count = data["data"]["stat"]["view"]
            print(f"视频播放量: {view_count}")
            return view_count
        else:
            print(f"API返回错误: {data['message']}")
            return None
            
    except Exception as e:
        print(f"获取播放量时出错: {e}")
        return None

def simulate_human_behavior(driver):
    """模拟人类行为"""
    try:
        # 随机滚动页面
        scroll_amount = random.randint(200, 800)
        driver.execute_script(f"window.scrollBy(0, {scroll_amount});")
        time.sleep(random.uniform(1, 3))
        
        # 随机滚动回部分
        driver.execute_script(f"window.scrollBy(0, -{scroll_amount//3});")
        
        # 模拟鼠标移动（通过JavaScript）
        driver.execute_script("""
            var evt = document.createEvent('MouseEvents');
            evt.initMouseEvent('mousemove', true, true, window, 0, 0, 0, 0, 0, false, false, false, false, 0, null);
            document.dispatchEvent(evt);
        """)
        
    except Exception as e:
        print(f"模拟行为时出错: {e}")

def main():
    """主函数"""
    # 循环设置
    cycles = 33550336  # 循环次数，可根据需要修改
    target_url = "https://www.bilibili.com/video/av114753305447594/"
    
    # 记录播放量变化
    view_history = []
    
    for i in range(cycles):
        print(f"\n{'='*50}")
        print(f"开始第 {i+1} 次循环")
        print(f"{'='*50}")
        
        driver = None
        try:
            # 设置浏览器选项
            chrome_options = get_normal_options()
            
            # 启动浏览器
            driver = webdriver.Chrome(
                service=Service(ChromeDriverManager().install()),
                options=chrome_options
            )
            
            # 执行防检测脚本
            driver.execute_script("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})")
            
            print(f"浏览器已启动 | UA: {get_random_ua()}")
            
            # 访问目标页面
            print(f"正在访问: {target_url}")
            driver.get(target_url)
            
            # 等待页面加载
            time.sleep(3)
            
            # 页面停留30秒，期间模拟人类行为
            print("页面停留30秒...")
            for sec in range(30):
                time.sleep(1)
                if sec % 10 == 0:  # 每10秒模拟一次行为
                    simulate_human_behavior(driver)
                print(f"\r已停留: {sec+1}/30秒", end="", flush=True)
            print()
            
            # 获取播放量信息
            print("通过API获取播放量信息...")
            view_count = get_video_views()
            
            if view_count is not None:
                view_history.append(view_count)
            
            # 清除浏览器数据
            clear_browser_data(driver)
            
        except Exception as e:
            print(f"第 {i+1} 次循环中出现错误: {e}")
            
        finally:
            # 确保浏览器关闭
            if driver:
                driver.quit()
                print("浏览器已关闭")
        
        # 如果不是最后一次循环，等待50秒
        if i < cycles - 1:
            print("等待50秒后开始下一次循环...")
            for sec in range(50):
                time.sleep(1)
                print(f"\r剩余等待时间: {50-sec}秒", end="", flush=True)
            print()
    
    # 输出播放量变化统计
    print(f"\n{'='*50}")
    print("所有循环执行完成！")
    if view_history:
        print(f"播放量变化记录: {view_history}")
        if len(view_history) > 1:
            change = view_history[-1] - view_history[0]
            print(f"总变化量: {change} (从 {view_history[0]} 到 {view_history[-1]})")
    print(f"{'='*50}")

if __name__ == "__main__":
    main()