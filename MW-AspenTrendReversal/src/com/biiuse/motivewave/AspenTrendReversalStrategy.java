package com.biiuse.motivewave;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.motivewave.platform.sdk.common.BarSize;
import com.motivewave.platform.sdk.common.DataContext;
import com.motivewave.platform.sdk.common.DataSeries;
import com.motivewave.platform.sdk.common.Defaults;
import com.motivewave.platform.sdk.common.Enums;
import com.motivewave.platform.sdk.common.Inputs;
import com.motivewave.platform.sdk.common.Instrument;
import com.motivewave.platform.sdk.common.Enums.BarSizeType;
import com.motivewave.platform.sdk.common.desc.BarSizeDescriptor;
import com.motivewave.platform.sdk.common.desc.BooleanDescriptor;
import com.motivewave.platform.sdk.common.desc.DoubleDescriptor;
import com.motivewave.platform.sdk.common.desc.IntegerDescriptor;
import com.motivewave.platform.sdk.common.desc.MarkerDescriptor;
import com.motivewave.platform.sdk.common.desc.SettingGroup;
import com.motivewave.platform.sdk.common.desc.SettingTab;
import com.motivewave.platform.sdk.common.desc.SettingsDescriptor;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.motivewave.platform.sdk.study.RuntimeDescriptor;
import com.motivewave.platform.sdk.study.StudyHeader;

/**
 * Trend reversal strategy that goes long / short if a new X day low or high is found at session close
 */
@StudyHeader(
		namespace = "com.biiuse", 
		id = "Aspen_Session_Close_High-Low_Strategy_v1.5", 
		name = "Aspen Trend Reversal Strategy v1.2", 
		desc = "Places and reversal positions on trend reversal indication", 
		menu = "Aspen", 
		overlay = true, 
		signals = false, 
		strategy = true, 
		autoEntry = true, 
		manualEntry = false, 
		supportsUnrealizedPL = true, 
		supportsRealizedPL = true, 
		supportsTotalPL = true, 
		supportsSessions = true, 
		sessions = 1, 
		supportsCloseOnSessionEnd = false)

public class AspenTrendReversalStrategy extends AspenTrendReversalStudySingleTimeFrame {
	
	
	final static String POSITION_SIZE = "positionSize";
	final static String STOP_LOSS_POINTS = "stopLossPoints";
	
	
	@Override
	public void initialize(Defaults defaults) {
		super.initialize(defaults);
		SettingsDescriptor sd = getSettingsDescriptor();
		SettingTab tab = (SettingTab) sd.getTabs().get(0); //get General Tab

		SettingGroup ma2 = new SettingGroup("Trade and Order Information");
		tab.addGroup(ma2);
		ma2.addRow(new IntegerDescriptor(POSITION_SIZE, "Position size", 10000, 1, 1000000, 1));
		ma2.addRow(new DoubleDescriptor(STOP_LOSS_POINTS, "Stop loss in terms of price", 50, 0, 9999, 1));
		
		
		//sd.addInvisibleSetting(new BarSizeDescriptor(Inputs.BARSIZE, "Daily Timefame", BarSize.getBarSize(BarSizeType.LINEAR, Enums.IntervalType.DAY, 1)));

		// Runtime Settings
		RuntimeDescriptor desc = getRuntimeDescriptor();
		
	    desc.setLabelSettings(Inputs.PERIOD, Inputs.INPUT, Inputs.INPUT2, Inputs.SHIFT, POSITION_SIZE);
	    desc.setLabelPrefix("Session Close HH/LL Strategy");

	}
	
	
	@Override
	public void onActivate(OrderContext ctx) {
		//check if parameters are well set
		//this.setMinBars(10);
		/*
		if (!this.isValidChartType(ctx.getDataContext())) {
			error ("Wrong bar type - Stragegy must be run on minute or hour bars. No trades will be taken");
			return;
		}
		*/
		
		/*
		int sessionCloseLookAHeadInMinutes = getSettings().getInteger(Inputs.SHIFT);
		
		
		if (chartMinuteInterval > sessionCloseLookAHeadInMinutes) {
			error ("Chart bar size must be less or equal than Session Close Look-a-head setting. No trades will be taken");
			return;
		}
		*/
		
		this.isActivated = true;
		info("Aspen Trend Reversal Strategy is active and trades may be taken");
		this.context = ctx;
		
		DateTimeFormatter dtfwithHours = DateTimeFormat.forPattern("yyyyMMddHHmmss");
		this.logFileName = dtfwithHours.print(new DateTime()) + "_" + ctx.getInstrument().getSymbol().replace("/", "") + ".csv";
		
	}
	
	@Override
	public void onDeactivate(OrderContext ctx) {
		this.isActivated = false;
		info("Aspen Trend Reversal Strategy is inactive - no trades will be taken");
		super.onDeactivate(ctx);
	}
	
	private void writeToCSV(DateTime timeStamp, Instrument ins, String tradeDirection, int positionSize, double entryPrice, double exitPrice, double PL, double drawDown) {
		//check if file exists or is empty
		boolean alreadyExists = new File(this.logFileName).exists();
		if (!alreadyExists) {
			try {
				new File(this.logFileName).createNewFile();
				BufferedWriter out = new BufferedWriter(new FileWriter(this.logFileName));
				out.write("DATE, SYMBOL, TRADE DIRECTION, POSITION SIZE, ENTRY PRICE, EXIT PRICE, P/L $, DRAW DOWN" + "\n");
				out.close();
			} 
			catch (IOException e) {
				info ("Could not write to log file" + e.toString());
			}
		}
		try {
			DateTimeFormatter dtfwithHours = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
			BufferedWriter out = new BufferedWriter(new FileWriter(this.logFileName, true));
			
			out.write(dtfwithHours.print(timeStamp) + "," + ins.getSymbol() + "," + tradeDirection + "," + positionSize + "," + String.format("%.5f",entryPrice) + "," + 
					String.format("%.5f",exitPrice) + "," + String.format("%.2f",PL) + ", " + String.format("%.5f",drawDown) + "\n");
			out.close();
		}
		catch (IOException e) {
			info ("Could not write to log file" + e.toString());
		}
	}
	
	
	@Override
	public void onSignal(OrderContext ctx, Object signal)
	  {
		info("OnSignal");
		
		if (!this.isActivated)
			return;
		
		int positionSize = getSettings().getInteger(POSITION_SIZE);
		DataSeries series = ctx.getDataContext().getDataSeries();
		DateTime barEndTime = new DateTime(series.getEndTime());
		DateTimeFormatter dtfwithHours = DateTimeFormat.forPattern("MM/dd/yyyy HH:mm:ss");
		
		
		if (ctx.getPosition() == 0) {
			if (signal == Signals.LOW) {
				info(dtfwithHours.print(barEndTime) + " " + barEndTime.getZone().getID() + " " + ctx.getInstrument().getSymbol() +  " New lowest low found at: "
						+ String.format("%.5f", series.getClose())
						+ ": Going LONG at market");
				ctx.buy(positionSize);
				entryPrice = series.getClose();
				return;
			}
			if (signal == Signals.HIGH) {
				info(dtfwithHours.print(barEndTime) + " " + barEndTime.getZone().getID() + " " + ctx.getInstrument().getSymbol() +  " New highest high found at: "
						+ String.format("%.5f", series.getClose())
						+ ": Going SHORT at market");
				ctx.sell(positionSize);
				entryPrice = series.getClose();
				entryDate = barEndTime;
				return;
			}
			
		}
		
		// if we are long look for position reversal
		if (ctx.getPosition() > 0) {
			if (signal == Signals.HIGH) {
				info(dtfwithHours.print(barEndTime) + " " + barEndTime.getZone().getID() + " " + ctx.getInstrument().getSymbol() + " New highest high found at: "
						+ String.format("%.5f", series.getClose())
						+ ": Reversing position to go SHORT at market");
				ctx.closeAtMarket();
				//log to CSV
				writeToCSV(entryDate, ctx.getInstrument() , "LONG", positionSize, entryPrice, series.getClose(), ctx.getRealizedPnL(), looserPips);
				ctx.sell(positionSize);
				entryPrice = series.getClose();
				entryDate = barEndTime;
				looserPips = 0.0;
				return;
			}
		}
		
		if (context.getPosition() < 0) {
			if (signal == Signals.LOW) {
				info(dtfwithHours.print(barEndTime) + " " + barEndTime.getZone().getID() + " " + context.getInstrument().getSymbol() + " New lowest low found at: "
						+ String.format("%.5f", series.getClose())
						+ ": Reversing position to go LONG at market");
				context.closeAtMarket();
				//log to CSV
				writeToCSV(entryDate, context.getInstrument() , "SHORT", positionSize, entryPrice, series.getClose(), context.getRealizedPnL(), looserPips);
				context.buy(positionSize);
				
				entryPrice = series.getClose();
				entryDate = barEndTime;
				looserPips = 0.0;
				return;
			}
		}
	}
	
	
	@Override
	protected void calculate(int index, DataContext ctx) {
		super.calculate(index, ctx);
		if (context == null) return;
	
		
		if (this.context.getPosition() < 0) {
			//if short
			if (ctx.getInstrument().getAskPrice() > this.entryPrice + getSettings().getDouble(STOP_LOSS_POINTS)) {
				context.closeAtMarket();
				int positionSize = getSettings().getInteger(POSITION_SIZE);
				writeToCSV(entryDate, context.getInstrument() , "SHORT", positionSize, entryPrice, ctx.getDataSeries().getClose(), context.getRealizedPnL(), looserPips);
			}
		}
		
		if (this.context.getPosition() > 0) {
			//if long
			if (ctx.getInstrument().getBidPrice() < this.entryPrice - getSettings().getDouble(STOP_LOSS_POINTS)) {
				int positionSize = getSettings().getInteger(POSITION_SIZE);
				context.closeAtMarket();
				writeToCSV(entryDate, context.getInstrument() , "LONG", positionSize, entryPrice, ctx.getDataSeries().getClose(), context.getRealizedPnL(), looserPips);
			}
		}
	}
	
	
		
	/*    
	@Override
	protected void calculate(int index, DataContext ctx) {
		
		if (!this.isActivated)
			return;

		// check if we are near session close
		DataSeries series = ctx.getDataSeries();
		if (!series.isBarComplete(index))
			return;

		int positionSize = getSettings().getInteger(Inputs.IND);
		int sessionCloseHours = getSettings().getInteger(Inputs.INPUT);
		int sessionCloseMinutes = getSettings().getInteger(Inputs.INPUT2);
		int sessionCloseLookAHeadInMinutes = getSettings().getInteger(Inputs.SHIFT);
		int lookBackPeriod = getSettings().getInteger(Inputs.PERIOD);

		int sessionCloseTotalMinutes = sessionCloseHours * 60 + sessionCloseMinutes;

		DateTime barEndTime = new DateTime(series.getEndTime());
		int barEndTotalMinutes = barEndTime.getHourOfDay() * 60
				+ barEndTime.getMinuteOfHour();

		// check if we are 'near' session close
		if ((barEndTotalMinutes <= sessionCloseTotalMinutes)
				&& (barEndTotalMinutes >= sessionCloseTotalMinutes
						- sessionCloseLookAHeadInMinutes)) {
			// get previous highest highs and lowest lows
			double previousLowestLow = 0;
			double previousHighestHigh = 0;
			try {
				previousLowestLow = getLowestCloseNonDayBars(ctx,
						lookBackPeriod, sessionCloseTotalMinutes);
				previousHighestHigh = getHighestCloseNonDayBars(ctx,
						lookBackPeriod, sessionCloseTotalMinutes);
			} catch (DataException e) {
				// info (e.toString());
				return;
			}

			if (!sufficientHistoricalDataAvailable) {
				// if we make it to this point then we were able to calculate
				// the previous lowest lows and highest highs and suff.
				// historical data is available.
				sufficientHistoricalDataAvailable = true;
				DateTimeFormatter dtfNoHours = DateTimeFormat.forPattern("MM/dd/yyyy");
				info("First effective tradind day is: " + dtfNoHours.print(barEndTime));
			}

			
			DateTimeFormatter dtfwithHours = DateTimeFormat.forPattern("MM/dd/yyyy HH:mm:ss");
			// if we have no open positions look for initial market entry
			if (context.getPosition() == 0) {
				if (series.getClose() < previousLowestLow) {
					info(dtfwithHours.print(barEndTime) + " " + barEndTime.getZone().getID() + " " + ctx.getInstrument().getSymbol() +  " New lowest low found at: "
							+ String.format("%.5f", series.getClose())
							+ ": Going LONG at market");
					context.buy(positionSize);
					entryPrice = series.getClose();
					entryDate = barEndTime;
					series.setComplete(index);
					return;
				}
				if (series.getClose() > previousHighestHigh) {
					info(dtfwithHours.print(barEndTime) + " " + barEndTime.getZone().getID() + " " + ctx.getInstrument().getSymbol() +  " New highest high found at: "
							+ String.format("%.5f", series.getClose())
							+ ": Going SHORT at market");
					context.sell(positionSize);
					entryPrice = series.getClose();
					entryDate = barEndTime;
					series.setComplete(index);
					return;
				}
			}
			// if we are long look for position reversal
			if (context.getPosition() > 0) {
				if (series.getClose() > previousHighestHigh) {
					info(dtfwithHours.print(barEndTime) + " " + barEndTime.getZone().getID() + " " + ctx.getInstrument().getSymbol() + " New highest high found at: "
							+ String.format("%.5f", series.getClose())
							+ ": Reversing position to go SHORT at market");
					context.closeAtMarket();
					//log to CSV
					writeToCSV(entryDate, ctx.getInstrument() , "LONG", positionSize, entryPrice, series.getClose(), context.getRealizedPnL());
					context.sell(positionSize);
					entryPrice = series.getClose();
					entryDate = barEndTime;
					series.setComplete(index);
					return;
				}
			}
			// if we are short look for position reversal
			if (context.getPosition() < 0) {
				if (series.getClose() < previousLowestLow) {
					info(dtfwithHours.print(barEndTime) + " " + barEndTime.getZone().getID() + " " + ctx.getInstrument().getSymbol() +  " New lowest low found at: "
							+ String.format("%.5f", series.getClose()) + " Previous lowest low was at: " + String.format("%.5f", previousLowestLow)
							+ ": Reversing position to go LONG at market");
					context.closeAtMarket();
					writeToCSV(entryDate, ctx.getInstrument() , "SHORT", positionSize, entryPrice, series.getClose(), context.getRealizedPnL());
					context.buy(positionSize);
					entryPrice = series.getClose();
					entryDate = barEndTime;
					series.setComplete(index);
					return;
				}
			}

		}
		
	}
	*/
	private OrderContext context;
	private boolean isActivated = false;
	private boolean sufficientHistoricalDataAvailable = false;
	private String logFileName;
	
	private double entryPrice;
	private DateTime entryDate;
	private double looserPips;
	
	
}
