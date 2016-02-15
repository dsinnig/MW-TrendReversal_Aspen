package com.biiuse.motivewave;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.motivewave.platform.sdk.common.DataContext;
import com.motivewave.platform.sdk.common.DataSeries;
import com.motivewave.platform.sdk.common.Defaults;
import com.motivewave.platform.sdk.common.Inputs;
import com.motivewave.platform.sdk.common.Instrument;
import com.motivewave.platform.sdk.common.desc.IntegerDescriptor;
import com.motivewave.platform.sdk.common.desc.SettingGroup;
import com.motivewave.platform.sdk.common.desc.SettingTab;
import com.motivewave.platform.sdk.common.desc.SettingsDescriptor;
import com.motivewave.platform.sdk.order_mgmt.OrderContext;
import com.motivewave.platform.sdk.study.RuntimeDescriptor;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;

/**
 * Trend reversal strategy that goes long / short if a new X day low or high is found at session close
 */
@StudyHeader(namespace = "com.biiuse", id = "AspenTrendReversal", name = "Aspen Trend Reversal", desc = "Places and reversal positions on trend reversal indication", menu = "Aspen", overlay = false, signals = false, strategy = true, autoEntry = true, manualEntry = false, supportsUnrealizedPL = true, supportsRealizedPL = true, supportsTotalPL = true, supportsSessions = true, sessions = 1, supportsCloseOnSessionEnd = false)
public class AspenTrendReversal extends Study {
	@Override
	public void initialize(Defaults defaults) {
		// User Settings
		SettingsDescriptor sd = new SettingsDescriptor();
		setSettingsDescriptor(sd);
		SettingTab tab = new SettingTab("General");
		sd.addTab(tab);

		SettingGroup ma1 = new SettingGroup("Look Back Days");
		tab.addGroup(ma1);
		ma1.addRow(new IntegerDescriptor(Inputs.PERIOD, "LookBackDays", 10, 1,
				9999, 1));
		ma1.addRow(new IntegerDescriptor(Inputs.INPUT, "Session Close Hours (UTC)",
				22, 0, 23, 1));
		ma1.addRow(new IntegerDescriptor(Inputs.INPUT2, "Session Close Minutes (UTC)",
				0, 0, 59, 1));
		ma1.addRow(new IntegerDescriptor(Inputs.SHIFT,
				"Session Close Look AHead In Minutes", 60, 0, 120, 1));
		ma1.addRow(new IntegerDescriptor(Inputs.IND,
				"Position size", 10000, 1, 999999999, 1));
		
		// Runtime Settings
		RuntimeDescriptor desc = new RuntimeDescriptor();
		setRuntimeDescriptor(desc);
		desc.setLabelSettings(Inputs.PERIOD);
	}

	@Override
	public void onActivate(OrderContext ctx) {
		//check if parameters are well set
		
		// Session close offset must be less than chart bar interval
		int chartMinuteInterval = ctx.getDataContext().getChartBarSize().getIntervalMinutes();
		if (chartMinuteInterval == 0) {
			error ("Wrong bar type - Stragegy must be run on minute or hour bars. No trades will be taken");
			return;
		}
		
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
	
	private void writeToCSV(DateTime timeStamp, Instrument ins, String tradeDirection, int positionSize, double entryPrice, double exitPrice, double PL) {
		//check if file exists or is empty
		boolean alreadyExists = new File(this.logFileName).exists();
		if (!alreadyExists) {
			try {
				new File(this.logFileName).createNewFile();
				BufferedWriter out = new BufferedWriter(new FileWriter(this.logFileName));
				out.write("DATE, SYMBOL, TRADE DIRECTION, POSITION SIZE, ENTRY PRICE, EXIT PRICE, P/L $" + "\n");
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
					String.format("%.5f",exitPrice) + "," + String.format("%.2f",PL) + "\n");
			out.close();
		}
		catch (IOException e) {
			info ("Could not write to log file" + e.toString());
		}
	}
	


/*
	private double getLowestClose(DataContext ctx, int lookBackDays)
			throws DataException {
		// get Daily bars
		DataSeries daily = ctx.getDataSeries(BarSize.getBarSize(
				Enums.BarSizeType.LINEAR, Enums.IntervalType.DAY, 1));
		// check if enough data
		if (daily.getEndIndex() < lookBackDays)
			throw new InsufficientDataException();

		int i = 1; // start with yesterday's session
		int offset = 0; // compensate if we have bars with 0 value
		double low = 99999999;

		while (i < lookBackDays + offset) {
			double bidClosePrice = daily.getBidClose(daily.getEndIndex() - i);
			if (bidClosePrice == 0.0) {
				++i;
				++offset;
				continue;
			} else {
				if (bidClosePrice < low)
					low = bidClosePrice;
				++i;
			}
		}
		if (low == 99999999)
			throw new DataException("Lowest low could not be established");
		return low;
	}
	
	private double getHighestClose(DataContext ctx, int lookBackDays)
			throws DataException {
		// get Daily bars
		DataSeries daily = ctx.getDataSeries(BarSize.getBarSize(
				Enums.BarSizeType.LINEAR, Enums.IntervalType.DAY, 1));
		// check if enough data
		if (daily.getEndIndex() < lookBackDays)
			throw new InsufficientDataException();

		int i = 1; // start with yesterday's session
		int offset = 0; // compensate if we have bars with 0 value

		double high = -1;

		while (i < lookBackDays + offset) {
			double askClosePrice = daily.getAskClose(daily.getEndIndex() - i);
			if (askClosePrice == 0.0) {
				++i;
				++offset;
				continue;
			} else {
				if (askClosePrice > high)
					high = askClosePrice;
				++i;
			}
		}
		if (high == -1)
			throw new DataException("Highest high could not be established");
		return high;
	}
*/

	private double getLowestCloseNonDayBars(DataContext ctx, int lookBackDays,
			int sessionCloseInMinutes) throws DataException {
		DataSeries series = ctx.getDataSeries();
		int numberOfSessionsAnalyzed = 0; // start with yesterday's session
		int barNumber = 10; // start with previous Bar
		double low = 99999999;

		while (numberOfSessionsAnalyzed < lookBackDays) {
			// info("BarNumber: " + barNumber + " EndIndex: " +
			// series.getEndIndex() + " Size: " + series.size());
			if (barNumber > series.size() - 1)
				throw new InsufficientDataException();

			DateTime barEndTime = new DateTime(series.getEndTime(series.size()
					- barNumber));
			int barEndTotalMinutes = barEndTime.getHourOfDay() * 60
					+ barEndTime.getMinuteOfHour();

			// check if it's session end bad
			if (sessionCloseInMinutes == barEndTotalMinutes)  {
				// info("Session end found");
				numberOfSessionsAnalyzed++;
				double closePrice = series.getClose(series.size()
						- barNumber);
				if (closePrice < low)
					low = closePrice;
			}
			barNumber++;
		}
		if (low == 99999999)
			throw new DataException("Lowest low could not be established");
		return low;
	}

	private double getHighestCloseNonDayBars(DataContext ctx, int lookBackDays,
			int sessionCloseInMinutes) throws DataException {
		DataSeries series = ctx.getDataSeries();
		int numberOfSessionsAnalyzed = 0; // start with yesterday's session
		int barNumber = 10; // start with previous Bar
		double high = -1;

		while (numberOfSessionsAnalyzed < lookBackDays) {
			// info("BarNumber: " + barNumber + " EndIndex: " +
			// series.getEndIndex() + " Size: " + series.size());
			if (barNumber > series.size() - 1)
				throw new InsufficientDataException();

			DateTime barEndTime = new DateTime(series.getEndTime(series.size()
					- barNumber));
			int barEndTotalMinutes = barEndTime.getHourOfDay() * 60
					+ barEndTime.getMinuteOfHour();

			// check if it's session end bad
			if (sessionCloseInMinutes == barEndTotalMinutes) {
				// info("Session end found");
				numberOfSessionsAnalyzed++;
				double closePrice = series.getClose(series.size()
						- barNumber);
				if (closePrice > high)
					high = closePrice;
			}
			barNumber++;
		}
		if (high == -1)
			throw new DataException("Lowest low could not be established");
		return high;
	}

	

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
		int sessionCloseLookAHeadInMinutes = getSettings().getInteger(
				Inputs.SHIFT);
		int lookBackPeriod = getSettings().getInteger(Inputs.PERIOD);

		int sessionCloseTotalMinutes = sessionCloseHours * 60
				+ sessionCloseMinutes;

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
	
	private OrderContext context;
	private boolean isActivated = false;
	private boolean sufficientHistoricalDataAvailable = false;
	private String logFileName;
	
	private double entryPrice;
	private DateTime entryDate;
}
