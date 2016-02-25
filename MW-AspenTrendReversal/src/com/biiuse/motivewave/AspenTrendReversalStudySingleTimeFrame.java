package com.biiuse.motivewave;

import java.awt.BasicStroke;
import java.awt.Color;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.Minutes;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.motivewave.platform.sdk.common.BarSize;
import com.motivewave.platform.sdk.common.Coordinate;
import com.motivewave.platform.sdk.common.DataContext;
import com.motivewave.platform.sdk.common.DataSeries;
import com.motivewave.platform.sdk.common.Defaults;
import com.motivewave.platform.sdk.common.Enums;
import com.motivewave.platform.sdk.common.Enums.BarSizeType;
import com.motivewave.platform.sdk.common.Enums.IntervalType;
import com.motivewave.platform.sdk.common.Inputs;
import com.motivewave.platform.sdk.common.MarkerInfo;
import com.motivewave.platform.sdk.common.desc.BarSizeDescriptor;
import com.motivewave.platform.sdk.common.desc.BooleanDescriptor;
import com.motivewave.platform.sdk.common.desc.IntegerDescriptor;
import com.motivewave.platform.sdk.common.desc.MarkerDescriptor;
import com.motivewave.platform.sdk.common.desc.SettingGroup;
import com.motivewave.platform.sdk.common.desc.SettingTab;
import com.motivewave.platform.sdk.common.desc.SettingsDescriptor;
import com.motivewave.platform.sdk.draw.Figure;
import com.motivewave.platform.sdk.draw.Label;
import com.motivewave.platform.sdk.draw.Line;
import com.motivewave.platform.sdk.draw.Marker;
import com.motivewave.platform.sdk.study.RuntimeDescriptor;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;

@StudyHeader(
		namespace = "com.biiuse", 
		id = "Aspen_Session_Close_High-Low_Study_1.2", 
		name = "Aspen Session Close High-Low Study v1.2", 
		desc = "Plots out new sesion close highs and lows", 
		menu = "Aspen", 
		overlay = true, 
		signals = true, 
		strategy = false, 
		autoEntry = false, 
		manualEntry = false, 
		supportsUnrealizedPL = false, 
		supportsRealizedPL = false, 
		supportsTotalPL = false, 
		supportsSessions = false)
public class AspenTrendReversalStudySingleTimeFrame extends Study {
	
	enum Signals { NONE, LOW, HIGH };
	
	@Override
	public void initialize(Defaults defaults) {
		// User Settings
		SettingsDescriptor sd = new SettingsDescriptor();
		setSettingsDescriptor(sd);
		SettingTab tab = new SettingTab("General");
		sd.addTab(tab);

		SettingGroup ma1 = new SettingGroup("Look Back and Session Information");
		tab.addGroup(ma1);
		ma1.addRow(new IntegerDescriptor(Inputs.PERIOD, "LookBackDays", 10, 1,
				9999, 1));
		ma1.addRow(new IntegerDescriptor(Inputs.INPUT, "Session Close Hours (EST)",
				17, 0, 23, 1));
		ma1.addRow(new IntegerDescriptor(Inputs.INPUT2, "Session Close Minutes (EST)",
				0, 0, 59, 1));
		ma1.addRow(new IntegerDescriptor(Inputs.SHIFT, "Session Close Look AHead In Minutes", 60, 0, 120, 1));
		
		ma1.addRow(new MarkerDescriptor(Inputs.UP_MARKER, "New HH/LL Marker", Enums.MarkerType.ARROW, Enums.Size.MEDIUM, defaults.getRed(), defaults.getLineColor(), true, true));
		
		
		ma1.addRow(new BooleanDescriptor(Inputs.IND, "Show end of session?", true));

		ma1.addRow(new BooleanDescriptor(Inputs.IND2, "Omit consecutive LL or HHs", true));

		
		// Runtime Settings
		RuntimeDescriptor desc = new RuntimeDescriptor();
		setRuntimeDescriptor(desc);
		// Signals
	    desc.declareSignal(Signals.HIGH, "Session close high");
	    desc.declareSignal(Signals.LOW, "Session close low");
		desc.setLabelSettings(Inputs.PERIOD, Inputs.INPUT, Inputs.INPUT2, Inputs.SHIFT);
	    desc.setLabelPrefix("Session Close HH/LL Study - rev");
	}
	
	private double getSessionCloseLow(DataContext ctx, int lookBackSessions, int barIndex) throws DataException {
		double low = 999999;
		int sessionCloseHours = getSettings().getInteger(Inputs.INPUT);
		int sessionCloseMinutes = getSettings().getInteger(Inputs.INPUT2);
		
		DateTime dateTimeOfCurrentBar = new DateTime(ctx.getDataSeries().getStartTime(barIndex)); 
		
		ZonedDateTime sessionCloseDateTimeInEST = ZonedDateTime.of(dateTimeOfCurrentBar.getYear(), 
															   dateTimeOfCurrentBar.getMonthOfYear(),
															   dateTimeOfCurrentBar.getDayOfMonth(), 
															   sessionCloseHours,
															   sessionCloseMinutes,
															   0,0,
															   ZoneId.of("America/New_York"));
		int numberOfSessionsAnalyzed = 0;
		while (numberOfSessionsAnalyzed < lookBackSessions) {
			//calculate the close date and time of the previous session in EST, ensure to skip weekends
			do {
				sessionCloseDateTimeInEST = sessionCloseDateTimeInEST.minusDays(1);
				sessionCloseDateTimeInEST = ZonedDateTime.of(sessionCloseDateTimeInEST.getYear(), 
															 sessionCloseDateTimeInEST.getMonthValue(),
															 sessionCloseDateTimeInEST.getDayOfMonth(),
															 sessionCloseHours,
															 sessionCloseMinutes,
															 0,0,
															 ZoneId.of("America/New_York"));
				
				
			} while ((sessionCloseDateTimeInEST.getDayOfWeek() == DayOfWeek.SUNDAY) ||
					 (sessionCloseDateTimeInEST.getDayOfWeek() == DayOfWeek.SATURDAY));
			
			//find corresponding time in EST
			DateTime sessionCloseDateTimeinUTC = new DateTime(sessionCloseDateTimeInEST.getYear(),
															  sessionCloseDateTimeInEST.getMonthValue(),
															  sessionCloseDateTimeInEST.getDayOfMonth(),
															  sessionCloseDateTimeInEST.getHour(),
															  sessionCloseDateTimeInEST.getMinute(),
															  sessionCloseDateTimeInEST.getSecond()).
															  minusHours(sessionCloseDateTimeInEST.getOffset().getTotalSeconds()/3600).
															  minusMinutes(ctx.getChartBarSize().getIntervalMinutes());
			
			//info ("Predicted session start bar in UTC: " + sessionCloseDateTimeinUTC);
			
			int indexOfSessionCloseBar = ctx.getDataSeries().findIndex(sessionCloseDateTimeinUTC.getMillis());
			if (indexOfSessionCloseBar == -1) throw new InsufficientDataException();
			double closeOfSession = ctx.getDataSeries().getClose(indexOfSessionCloseBar);
			if (closeOfSession < low) low = closeOfSession;
			numberOfSessionsAnalyzed++;
			//info ("Time of session start bar in UTC: " + new DateTime(ctx.getDataSeries().getStartTime(indexOfSessionCloseBar)));
		}
		
		if (low == 999999) throw new DataException("Lowest low could not be estblished");
		return low;
	}
	
	private double getSessionCloseHigh(DataContext ctx, int lookBackSessions, int barIndex) throws DataException {
		double high = -1;
		int sessionCloseHours = getSettings().getInteger(Inputs.INPUT);
		int sessionCloseMinutes = getSettings().getInteger(Inputs.INPUT2);
		
		DateTime dateTimeOfCurrentBar = new DateTime(ctx.getDataSeries().getStartTime(barIndex)); 
		
		ZonedDateTime sessionCloseDateTimeInEST = ZonedDateTime.of(dateTimeOfCurrentBar.getYear(), 
															   dateTimeOfCurrentBar.getMonthOfYear(),
															   dateTimeOfCurrentBar.getDayOfMonth(), 
															   sessionCloseHours,
															   sessionCloseMinutes,
															   0,0,
															   ZoneId.of("America/New_York"));
		int numberOfSessionsAnalyzed = 0;
		while (numberOfSessionsAnalyzed < lookBackSessions) {
			//calculate the close date and time of the previous session in EST, ensure to skip weekends
			do {
				sessionCloseDateTimeInEST = sessionCloseDateTimeInEST.minusDays(1);
				sessionCloseDateTimeInEST = ZonedDateTime.of(sessionCloseDateTimeInEST.getYear(), 
															 sessionCloseDateTimeInEST.getMonthValue(),
															 sessionCloseDateTimeInEST.getDayOfMonth(),
															 sessionCloseHours,
															 sessionCloseMinutes,
															 0,0,
															 ZoneId.of("America/New_York"));
				
				
			} while ((sessionCloseDateTimeInEST.getDayOfWeek() == DayOfWeek.SUNDAY) ||
					 (sessionCloseDateTimeInEST.getDayOfWeek() == DayOfWeek.SATURDAY));
			
			//find corresponding time in EST
			DateTime sessionCloseDateTimeinUTC = new DateTime(sessionCloseDateTimeInEST.getYear(),
															  sessionCloseDateTimeInEST.getMonthValue(),
															  sessionCloseDateTimeInEST.getDayOfMonth(),
															  sessionCloseDateTimeInEST.getHour(),
															  sessionCloseDateTimeInEST.getMinute(),
															  sessionCloseDateTimeInEST.getSecond()).
															  minusHours(sessionCloseDateTimeInEST.getOffset().getTotalSeconds()/3600).
															  minusMinutes(ctx.getChartBarSize().getIntervalMinutes());
			
			//info ("Predicted session start bar in UTC: " + sessionCloseDateTimeinUTC);
			
			int indexOfSessionCloseBar = ctx.getDataSeries().findIndex(sessionCloseDateTimeinUTC.getMillis());
			if (indexOfSessionCloseBar == -1) throw new InsufficientDataException();
			double closeOfSession = ctx.getDataSeries().getClose(indexOfSessionCloseBar);
			if (closeOfSession > high) high = closeOfSession;
			numberOfSessionsAnalyzed++;
			//info ("Time of session start bar in UTC: " + new DateTime(ctx.getDataSeries().getStartTime(indexOfSessionCloseBar)));
		}
		
		if (high == -1) throw new DataException("Lowest low could not be estblished");
		return high;
	}
	
	
	private double getLowFromDailyBars(DataContext ctx, int lookBackSessions, int barIndexOfCurrentChart) throws DataException{
		/*
		DateTime refBarStartTime = new DateTime(ctx.getDataSeries().getStartTime(barIndexOfCurrentChart));
		//respective index on Daily Chart
		
		
		int barIndexOnDailyChart;
		do {
			barIndexOnDailyChart = ctx.getDataSeries(BarSize.getBarSize(BarSizeType.LINEAR, Enums.IntervalType.DAY, 1)).findIndex(refBarStartTime.getMillis());
		} while (ctx.isLoadingData());
		*/
		
		int barIndexOnDailyChart = barIndexOfCurrentChart;
		
		//info("Size is: " + ctx.getDataSeries(BarSize.getBarSize(BarSizeType.LINEAR, Enums.IntervalType.DAY, 1)).size());
		
		//if (barIndexOnDailyChart > ctx.getDataSeries(BarSize.getBarSize(BarSizeType.LINEAR, Enums.IntervalType.DAY, 1)).size()) throw new InsufficientDataException();
		
		double low = 999999;
		for (int i = 1; i<=lookBackSessions;++i) {
			double closeOfSession = ctx.getDataSeries(BarSize.getBarSize(BarSizeType.LINEAR, Enums.IntervalType.DAY, 1)).getClose(barIndexOnDailyChart-i);
			if (closeOfSession < low) low = closeOfSession;
		}
		if (low == 999999) throw new DataException("Lowest low could not be estblished");
		return low;
	}
	
	private double getHighFromDailyBars(DataContext ctx, int lookBackSessions, int barIndexOfCurrentChart) throws DataException{
		/*
		DateTime refBarStartTime = new DateTime(ctx.getDataSeries().getStartTime(barIndexOfCurrentChart));
		//respective index on Daily Chart
		int barIndexOnDailyChart;
		
		do {
			barIndexOnDailyChart = ctx.getDataSeries(BarSize.getBarSize(BarSizeType.LINEAR, Enums.IntervalType.DAY, 1)).findIndex(refBarStartTime.getMillis());
		} while (ctx.isLoadingData());
		*/
		int barIndexOnDailyChart = barIndexOfCurrentChart;
		
		//info("Size is: " + ctx.getDataSeries(BarSize.getBarSize(BarSizeType.LINEAR, Enums.IntervalType.DAY, 1)).size());
		
		//if (barIndexOnDailyChart > ctx.getDataSeries(BarSize.getBarSize(BarSizeType.LINEAR, Enums.IntervalType.DAY, 1)).size()) throw new InsufficientDataException();
		
		double high = -1;
		for (int i = 1; i<=lookBackSessions;++i) {
			double closeOfSession = ctx.getDataSeries(BarSize.getBarSize(BarSizeType.LINEAR, Enums.IntervalType.DAY, 1)).getClose(barIndexOnDailyChart-i);
			if (closeOfSession > high) high = closeOfSession;
		}
		if (high == -1) throw new DataException("Highest high could not be estblished");
		return high;
	}
	
	protected boolean isValidChartType(DataContext ctx) {
		switch (ctx.getChartBarSize().getIntervalMinutes()) {
		case 1:
		case 2: 
		case 5: 
		case 10: 
		case 15:
		case 20: 
		case 30:
		case 60: return true;
		default: {
			error ("Study only works for: 1, 2, 5, 10, 20, 30, 60mins charts");
			return false;
			}
		}
	}
	
	
	@Override
	public void onNewDataSeries(DataContext ctx) {
		
		//isOnValidChart = isValidChartType(ctx);
		this.clearFigures();
		super.onNewDataSeries(ctx);
	}
	
	@Override
	public void onSettingsUpdated(DataContext ctx) {
		//isOnValidChart = isValidChartType(ctx);
		this.clearFigures();
		super.onSettingsUpdated(ctx);
	}
	
	
	@Override
	public  void onDataSeriesMoved(DataContext ctx) {
		//isOnValidChart = isValidChartType(ctx);
		super.onDataSeriesMoved(ctx);
		//this.clearFigures();
	};
	
	@Override
	public void onDataSeriesUpdated(DataContext ctx) {
		//isOnValidChart = isValidChartType(ctx);
		super.onDataSeriesUpdated(ctx);
		//this.clearFigures();
	}; 
	
	@Override
	protected void calculate(int index, DataContext ctx) {
		
		//if(!this.isOnValidChart) return;
		DateTimeFormatter dtfwithHours = DateTimeFormat.forPattern("MM/dd/yyyy HH:mm:ss");
		
		DataSeries series = ctx.getDataSeries();
		if (!series.isBarComplete(index))
			return;
		
		DateTime barEndTime = new DateTime(series.getEndTime(index));
		LocalDateTime javaTimeBarEndTime = LocalDateTime.of(barEndTime.getYear(), barEndTime.getMonthOfYear(), barEndTime.getDayOfMonth(), barEndTime.getHourOfDay(), barEndTime.getMinuteOfHour());
		ZonedDateTime barEndTimeInEST = ZonedDateTime.of(javaTimeBarEndTime, ZoneId.of("America/New_York"));
		
		int sessionCloseHours = getSettings().getInteger(Inputs.INPUT);
		int sessionCloseMinutes = getSettings().getInteger(Inputs.INPUT2);
		int lookBackPeriod = getSettings().getInteger(Inputs.PERIOD);
		
		int sessionCloseTotalMinutes = sessionCloseHours * 60 + sessionCloseMinutes;
		int barEndTotalMinutesinEST = barEndTimeInEST.plusSeconds(barEndTimeInEST.getOffset().getTotalSeconds()).getHour() * 60	+ barEndTimeInEST.plusSeconds(barEndTimeInEST.getOffset().getTotalSeconds()).getMinute();
		
		//check if we are at the end of the session
		if ((barEndTotalMinutesinEST == sessionCloseTotalMinutes) && (barEndTime.getDayOfWeek() != DateTimeConstants.SUNDAY)){
			if((getSettings().getBoolean(Inputs.IND)) && (!(ctx.getChartBarSize().getIntervalMinutes()>=1440))){
			
				Coordinate lineStart = new Coordinate(series.getStartTime(index), 0);
				Coordinate lineEnd = new Coordinate(series.getStartTime(index), 1000);
				Line sessionEndMarker = new Line(lineStart, lineEnd);
				sessionEndMarker.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5}, 0));
				sessionEndMarker.setColor(Color.GRAY);
				addFigure(sessionEndMarker);
			}
		}
			
		int lookAHead = getSettings().getInteger(Inputs.SHIFT);
		
		//info ("barEndTotalMinutesInEST: " + barEndTotalMinutesinEST + " sessioncloseTotalMinutes: " + sessionCloseTotalMinutes + " lookAhead: " + lookAHead);
		
		if ((ctx.getChartBarSize().getIntervalType() == IntervalType.DAY) || ((barEndTotalMinutesinEST >= sessionCloseTotalMinutes-lookAHead) && (barEndTotalMinutesinEST <= sessionCloseTotalMinutes) && (barEndTime.getDayOfWeek() != DateTimeConstants.SUNDAY))){
			// get previous highest highs and lowest lows
			double previousLowestLow = -1;
			double previousHighestHigh = 9999;
			try {
				previousLowestLow = getSessionCloseLow(ctx, lookBackPeriod, index);
				previousHighestHigh = getSessionCloseHigh(ctx, lookBackPeriod, index);
				/*
				if (ctx.getChartBarSize().getIntervalType() != IntervalType.DAY) {
					previousLowestLow = getSessionCloseLow(ctx, lookBackPeriod, index);
					previousHighestHigh = getSessionCloseHigh(ctx, lookBackPeriod, index);
				} else {
					previousLowestLow =  getLowFromDailyBars(ctx, lookBackPeriod, index);
					previousHighestHigh = getHighFromDailyBars(ctx, lookBackPeriod, index);
				
				
				} */
			} catch (DataException e) {
				 info (e.toString());
				return;
			}
			
			
			if (series.getClose(index) < previousLowestLow) {
				if ((getSettings().getBoolean(Inputs.IND2)) && (this.lastSignal != Signals.LOW)) {
					info(new DateTime(series.getEndTime(index)).toString() + " prevLL is: " + previousLowestLow + " close is" + series.getClose(index));
					this.lastSignal = Signals.LOW;
					deleteLastFigures(barEndTime, lookAHead);
					
					info(dtfwithHours.print(barEndTime) + ": New lowest low found");
					Coordinate c = new Coordinate(series.getStartTime(index), series.getLow(index));
					
					MarkerInfo marker = getSettings().getMarker(Inputs.UP_MARKER);
					Marker arrow = new Marker(c, Enums.Position.BOTTOM, marker);
					addFigure(arrow);
					
					Coordinate labelCoordinate = new Coordinate(series.getStartTime(index), series.getLow(index)); // - ((series.getHigh() - series.getLow())*0.7));
					Label priceLabel = new Label(labelCoordinate, "Low: " + String.format("%.5f", series.getClose(index)));
					addFigure(priceLabel);
					dateOfLastFigure = barEndTime;
					lastFigureMarker = arrow;
					lastFigureLabel = priceLabel;
					
				}
				
				ctx.signal(index, Signals.LOW, "New low on session close", series.getClose(index));
				
			}
				
			if (series.getClose(index) > previousHighestHigh) {
				if ((getSettings().getBoolean(Inputs.IND2)) && (this.lastSignal != Signals.HIGH)) {
					this.lastSignal = Signals.HIGH;
					
					deleteLastFigures(barEndTime, lookAHead);
					info(dtfwithHours.print(barEndTime) + ": New highest high found");
					Coordinate c = new Coordinate(series.getStartTime(index), series.getHigh(index));
					
					MarkerInfo marker = getSettings().getMarker(Inputs.UP_MARKER);
					Marker arrow = new Marker(c, Enums.Position.TOP, marker);
					addFigure(arrow);
					
					Coordinate labelCoordinate = new Coordinate(series.getStartTime(index), series.getHigh(index)); // + ((series.getHigh() - series.getLow())*0.7));
					Label priceLabel = new Label(labelCoordinate, "High: " + String.format("%.5f", series.getClose(index)));
					addFigure(priceLabel);
					dateOfLastFigure = barEndTime;
					lastFigureMarker = arrow;
					lastFigureLabel = priceLabel;
				}
				ctx.signal(index, Signals.HIGH, "New high on session close", series.getClose(index));
			}
		}
	}
	
	private void deleteLastFigures(DateTime barDate, int lookAhead) {
		//info("CurrBar: " + barDate + " Lastbar: " + dateOfLastFigure + " " + " Lookahead: " + lookAhead);
		if ((lastFigureMarker != null) && (lastFigureLabel != null)) {
			if (Minutes.minutesBetween(dateOfLastFigure, barDate).getMinutes() <= lookAhead) {
				//info("Diff is: " + Minutes.minutesBetween(barDate, dateOfLastFigure).getMinutes());
				this.removeFigure(lastFigureMarker);
				this.removeFigure(lastFigureLabel);
			}
		}
		
		
	}
	
	
	private DateTime dateOfLastFigure;
	private Figure lastFigureMarker = null;
	private Figure lastFigureLabel = null;
	private Signals lastSignal = Signals.NONE;
}
