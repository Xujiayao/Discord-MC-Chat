package com.xujiayao.discord_mc_chat.common;

import com.xujiayao.discord_mc_chat.common.config.ConfigManager;
import com.xujiayao.discord_mc_chat.common.core.RunModeManager;
import com.xujiayao.discord_mc_chat.common.utils.Utils;
import com.xujiayao.discord_mc_chat.common.utils.logging.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Discord-MC-Chat 主类
 * 负责模组的初始化和生命周期管理
 * 
 * @author Xujiayao
 */
public class DMCC {

	public static String VERSION;
	public static final Logger LOGGER = new Logger();
	
	private static DMCC instance;
	private static boolean initialized = false;
	
	// 核心组件
	private ConfigManager configManager;
	private ScheduledExecutorService scheduledExecutor;
	
	// 运行状态
	private boolean running = false;
	private long startTime;

	public static void main(String[] args) {
		// 独立模式启动入口
		init("Standalone");
	}

	/**
	 * 初始化DMCC（通过加载器名称自动获取版本）
	 * 
	 * @param loader 加载器名称（"Fabric", "NeoForge", "Standalone"）
	 */
	public static void init(String loader) {
		init(loader, Utils.getVersionByResource());
	}

	/**
	 * 初始化DMCC
	 * 
	 * @param loader 加载器名称
	 * @param version 版本号
	 */
	public static void init(String loader, String version) {
		if (initialized) {
			LOGGER.warn("DMCC已经初始化，忽略重复初始化");
			return;
		}
		
		VERSION = version;
		LOGGER.info("正在初始化 DMCC {} (加载器: {})", VERSION, loader);
		
		try {
			// 初始化运行模式管理器
			RunModeManager.initialize(loader);
			
			// 创建实例并开始初始化流程
			instance = new DMCC();
			instance.startInitialization();
			
			initialized = true;
			LOGGER.info("DMCC {} 初始化完成", VERSION);
			
		} catch (Exception e) {
			LOGGER.error("DMCC 初始化失败: {}", e.getMessage(), e);
			throw new RuntimeException("DMCC 初始化失败", e);
		}
	}
	
	/**
	 * 开始初始化流程
	 */
	private void startInitialization() {
		startTime = System.currentTimeMillis();
		
		LOGGER.info("开始初始化核心组件...");
		
		// 1. 初始化配置管理器
		initializeConfigManager();
		
		// 2. 初始化线程池
		initializeExecutorService();
		
		// 3. 初始化其他核心组件
		initializeCoreComponents();
		
		// 4. 设置运行状态
		running = true;
		
		// 5. 注册关闭钩子
		registerShutdownHook();
		
		LOGGER.info("核心组件初始化完成，耗时: {}ms", System.currentTimeMillis() - startTime);
	}
	
	/**
	 * 初始化配置管理器
	 */
	private void initializeConfigManager() {
		LOGGER.info("初始化配置管理器...");
		configManager = ConfigManager.getInstance();
		configManager.loadConfigs();
	}
	
	/**
	 * 初始化线程池
	 */
	private void initializeExecutorService() {
		LOGGER.info("初始化线程池...");
		int threadPoolSize = configManager.getPerformanceConfig().getThreadPool().getCoreSize();
		scheduledExecutor = Executors.newScheduledThreadPool(threadPoolSize, r -> {
			Thread thread = new Thread(r, "DMCC-Task");
			thread.setDaemon(true);
			return thread;
		});
	}
	
	/**
	 * 初始化其他核心组件
	 */
	private void initializeCoreComponents() {
		LOGGER.info("初始化核心组件...");
		
		// TODO: 初始化Discord模块
		// TODO: 初始化Minecraft模块（仅模组模式）
		// TODO: 初始化工具模块
		// TODO: 初始化账户链接系统
		// TODO: 启动监控服务
		
		RunModeManager runModeManager = RunModeManager.getInstance();
		if (runModeManager.isModMode()) {
			LOGGER.info("模组模式：初始化Minecraft集成组件");
			// 初始化Minecraft相关组件
		} else {
			LOGGER.info("独立模式：跳过Minecraft集成组件");
		}
	}
	
	/**
	 * 注册JVM关闭钩子
	 */
	private void registerShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			LOGGER.info("检测到系统关闭信号，开始清理资源...");
			shutdown();
		}, "DMCC-Shutdown"));
	}
	
	/**
	 * 关闭DMCC
	 */
	public static void shutdown() {
		if (instance != null && instance.running) {
			instance.doShutdown();
		}
	}
	
	/**
	 * 执行关闭流程
	 */
	private void doShutdown() {
		LOGGER.info("开始关闭 DMCC...");
		running = false;
		
		try {
			// 1. 停止定时任务
			if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
				LOGGER.info("关闭定时任务...");
				scheduledExecutor.shutdown();
				if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
					LOGGER.warn("定时任务未在5秒内完成，强制关闭");
					scheduledExecutor.shutdownNow();
				}
			}
			
			// 2. 关闭其他组件
			// TODO: 关闭Discord连接
			// TODO: 关闭Minecraft组件（仅模组模式）
			// TODO: 关闭其他服务
			
			LOGGER.info("DMCC 关闭完成");
			
		} catch (Exception e) {
			LOGGER.error("关闭过程中发生错误: {}", e.getMessage(), e);
		}
	}
	
	/**
	 * 获取DMCC实例
	 * 
	 * @return DMCC实例
	 */
	public static DMCC getInstance() {
		return instance;
	}
	
	/**
	 * 获取配置管理器
	 * 
	 * @return 配置管理器
	 */
	public ConfigManager getConfigManager() {
		return configManager;
	}
	
	/**
	 * 获取调度执行器
	 * 
	 * @return 调度执行器
	 */
	public ScheduledExecutorService getScheduledExecutor() {
		return scheduledExecutor;
	}
	
	/**
	 * 是否正在运行
	 * 
	 * @return 运行状态
	 */
	public boolean isRunning() {
		return running;
	}
	
	/**
	 * 获取启动时间
	 * 
	 * @return 启动时间戳
	 */
	public long getStartTime() {
		return startTime;
	}
	
	/**
	 * 获取运行时长（毫秒）
	 * 
	 * @return 运行时长
	 */
	public long getUptime() {
		return System.currentTimeMillis() - startTime;
	}
}
