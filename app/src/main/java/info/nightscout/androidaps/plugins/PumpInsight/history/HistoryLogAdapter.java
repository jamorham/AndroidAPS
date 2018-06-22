package info.nightscout.androidaps.plugins.PumpInsight.history;

import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;

/**
 * Created by jamorham on 27/01/2018.
 * <p>
 * Write to the History Log
 */

class HistoryLogAdapter {

    private static final long MAX_TIME_DIFFERENCE = 61000;
    private static final long MAX_BOLUS_TIME_DIFFERENCE = 30000;

    private static void log(String msg) {
        android.util.Log.e("HISTORYLOG", msg);
    }

    void createTBRrecord(Date eventDate, int percent, int duration, long record_id) {

        TemporaryBasal temporaryBasal = new TemporaryBasal().date(eventDate.getTime());

        final TemporaryBasal temporaryBasalFromHistory = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(eventDate.getTime());

        if (temporaryBasalFromHistory == null) {
            log("Create new TBR: " + eventDate + " " + percent + " " + duration);
        } else {
            log("Loaded existing TBR record: " + temporaryBasalFromHistory.toString());
            if (Math.abs(eventDate.getTime() - temporaryBasalFromHistory.date) < MAX_TIME_DIFFERENCE) {
                if (temporaryBasalFromHistory.source != Source.PUMP) {
                    if (temporaryBasalFromHistory.percentRate == percent) {
                        log("Things seem to match: %" + percent);
                        temporaryBasal = temporaryBasalFromHistory;
                        MainApp.getDbHelper().delete(temporaryBasalFromHistory);
                    } else {
                        log("This record has different percent rates: " + temporaryBasalFromHistory.percentRate + " vs us: " + percent);
                    }
                } else {
                    log("This record is already a pump record!");
                }
            } else {
                log("Time difference too great! : " + (eventDate.getTime() - temporaryBasalFromHistory.date));
            }
        }

        temporaryBasal.source(Source.PUMP)
                .pumpId(record_id)
                .percent(percent)
                .duration(duration);

        TreatmentsPlugin.getPlugin().addToHistoryTempBasal(temporaryBasal);
    }

    void createExtendedBolusRecord(Date eventDate, double insulin, int durationInMinutes, long record_id) {

        // TODO trap items below minimum period
        // TODO extended bolus will likely need same treatment as standard bolus below

        final ExtendedBolus extendedBolus = new ExtendedBolus();
        extendedBolus.date = eventDate.getTime();
        extendedBolus.insulin = insulin;
        extendedBolus.durationInMinutes = durationInMinutes;
        extendedBolus.source = Source.PUMP;
        extendedBolus.pumpId = record_id;

        TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
    }

    void createStandardBolusRecord(Date eventDate, double insulin, long record_id) {
        final Treatment bolusFromHistory = TreatmentsPlugin.getPlugin().getService().getBolusFromHistoryNearestToTime(eventDate.getTime());
        if ((bolusFromHistory != null) && (bolusFromHistory.insulin == insulin)
                && (Math.abs(bolusFromHistory.date - eventDate.getTime()) < MAX_BOLUS_TIME_DIFFERENCE)) {
            if (bolusFromHistory.source != Source.PUMP) {
                log("Updating existing pump record: old record:  diff: " + Math.abs(bolusFromHistory.date - eventDate.getTime()) + "  " + bolusFromHistory.toString());
                bolusFromHistory.source = Source.PUMP;
                bolusFromHistory.pumpId = record_id;
                TreatmentsPlugin.getPlugin().getService().update(bolusFromHistory);
                log("Updating existing pump record: " + bolusFromHistory.toString());
            } else {
                log("Not Updating existing pump record as it is already marked as from pump");
            }
        } else {
            final DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
            detailedBolusInfo.date = eventDate.getTime();
            detailedBolusInfo.source = Source.PUMP;
            detailedBolusInfo.pumpId = record_id;
            detailedBolusInfo.insulin = insulin;
            TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo);
            log("Creating new pump bolus record: " + detailedBolusInfo.toString());
        }
    }

}
