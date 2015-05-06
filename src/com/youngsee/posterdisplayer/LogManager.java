package com.youngsee.posterdisplayer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.youngsee.common.FileUtils;
import com.youngsee.common.Logger;
import com.youngsee.ftpoperation.FtpFileInfo;
import com.youngsee.ftpoperation.FtpHelper;
import com.youngsee.ftpoperation.FtpOperationInterface;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.format.Time;

public class LogManager {
	private Logger logger = new Logger();
	
	private static LogManager INSTANCE = null;
	
	private final String COMMAND_SU = "su";
	private final String COMMAND_EXIT = "exit\n";
	private final String COMMAND_LINE_END = "\n";
	
	public static final int LOGTYPE_NORMAL = 0;
	public static final int LOGTYPE_PERIOD = 1;
	
	private final int LOGMODULE_MAIN = 0;
	private final int LOGMODULE_SYSTEM = 1;
	private final int LOGMODULE_EVENTS = 2;
	
	private final int EVENT_TIMEOUT = 0x9000;
	
	private final long DEFAULT_THREAD_PERIOD = 1000;
	
	private final int DEFAULT_MAX_LOG_TASK_NUM = 5;
	
	private final String DEFAULT_TEMPLOG_DIR = "ystemplog";
	
	private final String LOGCMD_PREFIX = "logcat ";
	
	private final String LOGCMDPARAM_MAIN = "-b main";
	private final String LOGCMDPARAM_SYSTEM = "-b system";
	private final String LOGCMDPARAM_EVENTS = "-b events";
	private final String LOGCMDPARAM_FULL = "-b main -b system -b events";
	
	private final String LOGCMD_SUFFIX_NORMAL = " -v time -d";
	private final String LOGCMD_SUFFIX_PERIOD = " -v time";
	
	private final String MAINLOG_PREFIX = "main_";
	private final String SYSTEMLOG_PREFIX = "system_";
	private final String EVENTSLOG_PREFIX = "events_";
	private final String FULLLOG_PREFIX = "full_";
	private final String LOGFILE_EXTENSION = ".log";
	
	private HandlerThread mHandlerThread = null;
	private MyHandler mHandler = null;
	
	private LogManagerThread mLogManagerThread = null;
	
	private LinkedList<LogTaskInfo> mLogTaskList = null;
	
	private class LogTaskInfo {
		public int type;
		public long millisec;
		public Timer timer;
		public List<LogThreadInfo> logthdinfolist;
		public boolean inprogress;
	}
	
	private class LogThreadInfo {
		public String file;
		public Thread logthd;
	}
	
	public static LogManager getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new LogManager();
		}
		return INSTANCE;
	}
	
	private LogManager() {
		mHandlerThread = new HandlerThread("logmgr_ui_thread");
		mHandlerThread.start();
		mHandler = new MyHandler(mHandlerThread.getLooper());
	}
	
	public void destroy() {
		clearLogTask();
		if (mHandler != null) {
			mHandler.removeMessages(EVENT_TIMEOUT);
			mHandler = null;
		}
		if (mHandlerThread != null) {
			mHandlerThread.getLooper().quit();
			mHandlerThread = null;
		}
	}
	
	private void clearLogTask() {
		synchronized (mLogTaskList) {
			if (mLogManagerThread != null) {
				mLogManagerThread.interrupt();
			}
			
			if (mLogTaskList != null) {
				for (LogTaskInfo info : mLogTaskList) {
					if (info.logthdinfolist != null) {
						for (LogThreadInfo thdinfo : info.logthdinfolist) {
							thdinfo.logthd.interrupt();
						}
						info.logthdinfolist.clear();
						info.logthdinfolist = null;
					}
					if (info.timer != null) {
						info.timer.cancel();
						info.timer = null;
					}
				}
				mLogTaskList.clear();
				mLogTaskList = null;
			}
		}
	}
	
	private final class LogTimerTask extends TimerTask {
		private LogTaskInfo info;
		
		public LogTimerTask(LogTaskInfo info) {
			super();
			this.info = info;
		}
		
		@Override
		public void run() {
			if (info.logthdinfolist != null) {
				for (LogThreadInfo thdinfo : info.logthdinfolist) {
					thdinfo.logthd.interrupt();
				}
			}
			if (info.timer != null) {
				info.timer.cancel();
				info.timer = null;
			}
		}
	}
	
	private String getCurrentTime() {
		Time t = new Time();
        t.setToNow();
        
        StringBuilder sb = new StringBuilder();
        sb.append(t.year);
        sb.append(((t.month + 1) < 10) ? ("0" + (t.month + 1)) : (t.month + 1));
        sb.append((t.monthDay < 10) ? ("0" + t.monthDay) : t.monthDay);
        sb.append("-");
        sb.append((t.hour < 10) ? ("0" + t.hour) : t.hour);
        sb.append((t.minute < 10) ? ("0" + t.minute) : t.minute);
        sb.append((t.second < 10) ? ("0" + t.second) : t.second);
        
        return sb.toString();
	}

	private String getTempLogFilePath(final int logmodule, final String mac, final String time) {
		StringBuilder sb = new StringBuilder();
		sb.append(FileUtils.getExternalStorage());
		sb.append(File.separator);
		sb.append(DEFAULT_TEMPLOG_DIR);
		
		if (!FileUtils.isExist(sb.toString())) {
            FileUtils.createDir(sb.toString());
        }
		
		sb.append(File.separator);
		switch (logmodule) {
		case LOGMODULE_MAIN:
			sb.append(MAINLOG_PREFIX);
			break;
		case LOGMODULE_SYSTEM:
			sb.append(SYSTEMLOG_PREFIX);
			break;
		case LOGMODULE_EVENTS:
			sb.append(EVENTSLOG_PREFIX);
			break;
		default:
			sb.append(FULLLOG_PREFIX);
		}
		sb.append(mac);
		sb.append("_");
        sb.append(time);
        
        sb.append(LOGFILE_EXTENSION);
        
        return sb.toString();
	}
	
	private void removeLogThreadInfoFromList(String file, LogTaskInfo taskinfo) {
		if (taskinfo == null) {
			logger.i("The taskinfo is null.");
			return;
		}
		if (taskinfo.logthdinfolist == null) {
			logger.i("The taskinfo.logthdinfolist is null.");
			return;
		}
		
		for (LogThreadInfo thdinfo : taskinfo.logthdinfolist) {
			if (thdinfo.file.equals(file)) {
				if (!taskinfo.logthdinfolist.remove(thdinfo)) {
					logger.i("Failed to remove the thread info: "+thdinfo);
				}
				return;
			}
		}
	}
	
	private void clearTaskInfoList(String file, LogTaskInfo info) {
		removeLogThreadInfoFromList(file, info);
		if ((info != null) && (info.logthdinfolist != null) && info.logthdinfolist.isEmpty()) {
			info.logthdinfolist = null;
			delTaskInfoFromList(info);
		}
	}
	
	private void uploadFile(final String file, final LogTaskInfo info) {
		if (!FileUtils.isExist(file)) {
			logger.i("The local file isn't exist.");
			return;
		}
		
		FtpFileInfo toUploadFile = new FtpFileInfo();
        toUploadFile.setRemotePath("/logs");
        toUploadFile.setLocalPath(file);
        List<FtpFileInfo> toUploadList = new ArrayList<FtpFileInfo>();
        toUploadList.add(toUploadFile);
        
        FtpHelper.getInstance().uploadFileList(toUploadList, new FtpOperationInterface() {
			@Override
			public void started(String file, long size) {
			}

			@Override
			public void aborted() {
				logger.i("Aborted to upload the file: '"+file+"'.");
				FileUtils.delFile(file);
				clearTaskInfoList(file, info);
			}

			@Override
			public void progress(long length) {
			}

			@Override
			public void completed() {
				logger.i("Completed to upload the file: '"+file+"'.");
				FileUtils.delFile(file);
				clearTaskInfoList(file, info);
			}

			@Override
			public void failed() {
				logger.i("Failed to upload the file: '"+file+"'.");
				FileUtils.delFile(file);
				clearTaskInfoList(file, info);
			}
        });
	}
	
	private void delTaskInfoFromList(LogTaskInfo info) {
		synchronized (mLogTaskList) {
			if ((mLogTaskList != null) && mLogTaskList.contains(info)) {
				if (!mLogTaskList.remove(info)) {
					logger.i("Failed to remove the task info: "+info);
				}
			}
		}
	}
	
	private byte[] getCommand(final int logtype, final int logmodule) {
		StringBuilder cmd = new StringBuilder();
		
		cmd.append(LOGCMD_PREFIX);
		
		switch (logmodule) {
		case LOGMODULE_MAIN:
			cmd.append(LOGCMDPARAM_MAIN);
			break;
		case LOGMODULE_SYSTEM:
			cmd.append(LOGCMDPARAM_SYSTEM);
			break;
		case LOGMODULE_EVENTS:
			cmd.append(LOGCMDPARAM_EVENTS);
			break;
		default:
			cmd.append(LOGCMDPARAM_FULL);
		}
		
		switch (logtype) {
		case LOGTYPE_NORMAL:
			cmd.append(LOGCMD_SUFFIX_NORMAL);
			break;
		case LOGTYPE_PERIOD:
			cmd.append(LOGCMD_SUFFIX_PERIOD);
			break;
		default:
			cmd.append(LOGCMD_SUFFIX_NORMAL);
		}
		
		return cmd.toString().getBytes();
	}
	
	private void doUploadLogForNormal(final LogTaskInfo info, final int logmodule,
			final String mac, final String time) {
		final String tmpFile = getTempLogFilePath(logmodule, mac, time);
		LogThreadInfo logthdinfo = new LogThreadInfo();
		logthdinfo.file = tmpFile;
		logthdinfo.logthd = new Thread() {
			@Override
			public void run() {
				Process process = null;
				DataOutputStream os = null;
				BufferedReader br = null;
				try {
					process = Runtime.getRuntime().exec(COMMAND_SU);
					os = new DataOutputStream(process.getOutputStream());
					os.write(getCommand(info.type, logmodule));
					os.writeBytes(COMMAND_LINE_END);
					os.flush();
					os.writeBytes(COMMAND_EXIT);
					os.flush();
					
					br = new BufferedReader(
							new InputStreamReader(process.getInputStream(), "UTF-8"));

					StringBuilder log = new StringBuilder();
					String line;
					while ((line = br.readLine()) != null) {
						log.append(line);
						log.append("\n");
					}
					
					FileUtils.writeTextToFile(tmpFile, log.toString());
					uploadFile(tmpFile, info);
				} catch (Exception e) {
					e.printStackTrace();
					clearTaskInfoList(tmpFile, info);
				} finally {
					try {
						if (os != null) {
							os.close();
						}
						if (br != null) {
							br.close();
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					if (process != null) {
						process.destroy();
					}
				}
			}
		};
		logthdinfo.logthd.start();
		info.logthdinfolist.add(logthdinfo);
	}
	
	private void uploadLogForNormal(final LogTaskInfo info) {
		info.logthdinfolist = new ArrayList<LogThreadInfo>();
		
		String mac = PosterApplication.getEthMacStr();
		String time = getCurrentTime();

		doUploadLogForNormal(info, LOGMODULE_MAIN, mac, time);
		doUploadLogForNormal(info, LOGMODULE_SYSTEM, mac, time);
		doUploadLogForNormal(info, LOGMODULE_EVENTS, mac, time);
	}
	
	private final class LogThread extends Thread {
		private LogTaskInfo info;
		private String file;
		private byte[] cmd;
		
		public LogThread(LogTaskInfo info, String file, byte[] cmd) {
			super();
			this.info = info;
			this.file = file;
			this.cmd = cmd;
		}
		
		@Override
		public void run() {
			Process process = null;
			DataOutputStream os = null;
			BufferedReader br = null;
			StringBuilder log = null;
			try {
				process = Runtime.getRuntime().exec(COMMAND_SU);
				os = new DataOutputStream(process.getOutputStream());
				os.write(cmd);
				os.writeBytes(COMMAND_LINE_END);
				os.flush();
				os.writeBytes(COMMAND_EXIT);
				os.flush();
				
				br = new BufferedReader(
						new InputStreamReader(process.getInputStream(), "UTF-8"));

				log = new StringBuilder();
				while (!isInterrupted()) {
					if (br.ready()) {
						log.append(br.readLine());
						log.append("\n");
					}
					Thread.sleep(10);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (log != null) {
					FileUtils.writeTextToFile(file, log.toString());
					uploadFile(file, info);
				} else {
					clearTaskInfoList(file, info);
				}
				try {
					if (os != null) {
						os.close();
					}
					if (br != null) {
						br.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				if (process != null) {
					process.destroy();
				}
			}
		}
	}

	private void uploadLogForPeriod(final LogTaskInfo info) {
		info.timer = new Timer();
		info.logthdinfolist = new ArrayList<LogThreadInfo>();
		
		String mac = PosterApplication.getEthMacStr();
		String time = getCurrentTime();
		
		String tmpFile = null;
		byte[] cmd = null;
		
		LogThreadInfo logthdinfo = null;

		tmpFile = getTempLogFilePath(LOGMODULE_MAIN, mac, time);
		cmd = getCommand(info.type, LOGMODULE_MAIN);
		logthdinfo = new LogThreadInfo();
		logthdinfo.file = tmpFile;
		logthdinfo.logthd = new LogThread(info, tmpFile, cmd);
		logthdinfo.logthd.start();
		info.logthdinfolist.add(logthdinfo);
		
		tmpFile = getTempLogFilePath(LOGMODULE_SYSTEM, mac, time);
		cmd = getCommand(info.type, LOGMODULE_SYSTEM);
		logthdinfo = new LogThreadInfo();
		logthdinfo.file = tmpFile;
		logthdinfo.logthd = new LogThread(info, tmpFile, cmd);
		logthdinfo.logthd.start();
		info.logthdinfolist.add(logthdinfo);
		
		tmpFile = getTempLogFilePath(LOGMODULE_EVENTS, mac, time);
		cmd = getCommand(info.type, LOGMODULE_EVENTS);
		logthdinfo = new LogThreadInfo();
		logthdinfo.file = tmpFile;
		logthdinfo.logthd = new LogThread(info, tmpFile, cmd);
		logthdinfo.logthd.start();
		info.logthdinfolist.add(logthdinfo);
		
		info.timer.schedule(new LogTimerTask(info), info.millisec);
	}
	
	private LogTaskInfo findNextTaskInfo() {
		synchronized (mLogTaskList) {
			if (mLogTaskList != null) {
				for (LogTaskInfo info : mLogTaskList) {
					if (!info.inprogress) {
						return info;
					}
				}
			}
			return null;
		}
	}
	
	private final class LogManagerThread extends Thread {
		@Override
		public void run() {
			try {
				while (!isInterrupted()) {
					if ((mLogTaskList != null) && mLogTaskList.isEmpty()) {
						delAllTemplogs();
						mLogTaskList = null;
						mLogManagerThread = null;
						break;
					}
					
					LogTaskInfo info = findNextTaskInfo();
					if (info != null) {
						info.inprogress = true;
						switch (info.type) {
						case LOGTYPE_NORMAL:
							uploadLogForNormal(info);
							break;
						case LOGTYPE_PERIOD:
							uploadLogForPeriod(info);
							break;
						default:
							logger.e("The type is invalid. Just removes it.");
							mLogTaskList.remove(info);
						}
					}
					
					Thread.sleep(DEFAULT_THREAD_PERIOD);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void delAllTemplogs() {
		StringBuilder sb = new StringBuilder();
		sb.append(FileUtils.getExternalStorage());
		sb.append(File.separator);
		sb.append(DEFAULT_TEMPLOG_DIR);
		
		FileUtils.delDir(sb.toString());
	}
	
	public synchronized void uploadLog(int type, long millisec) {
		if ((type != LOGTYPE_NORMAL) && (type != LOGTYPE_PERIOD)) {
			logger.e("The type is invalid, type = "+type+".");
			return;
		}
		if ((mLogTaskList != null) && (mLogTaskList.size() > DEFAULT_MAX_LOG_TASK_NUM)) {
			logger.i("The task number has been the max value.");
			return;
		}
		
		if (mLogTaskList == null) {
			mLogTaskList = new LinkedList<LogTaskInfo>();
		}
		
		LogTaskInfo info = new LogTaskInfo();
		info.type = type;
		info.millisec = millisec;
		info.timer = null;
		info.logthdinfolist = null;
		info.inprogress = false;
		
		mLogTaskList.addLast(info);
		
		if (mLogManagerThread == null){
			mLogManagerThread = new LogManagerThread();
			mLogManagerThread.start();
		}
	}
	
	private class MyHandler extends Handler {
		public MyHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
            case EVENT_TIMEOUT:
            	
            	break;
            default:
            	
                break;
            }
            super.handleMessage(msg);
		}
	}
}
