package org.bonitasoft.truckmilk.schedule;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEvent.Level;
import org.bonitasoft.log.event.BEventFactory;
import org.bonitasoft.truckmilk.engine.MilkSerializeProperties;
import org.bonitasoft.truckmilk.engine.MilkSerializeProperties.SerializeOperation;
import org.bonitasoft.truckmilk.schedule.MilkSchedulerInt.StatusScheduler;
import org.bonitasoft.truckmilk.schedule.MilkSchedulerInt.TypeScheduler;
import org.bonitasoft.truckmilk.schedule.MilkSchedulerInt.TypeStatus;

/**
 * this class manage the different scheduler, and give access to the scheduler selected
 */
public class MilkSchedulerFactory {

    private static MilkSchedulerFactory milkSchedulerFactory = new MilkSchedulerFactory();

    private static BEvent eventUnknowSchedulerType = new BEvent(MilkSchedulerFactory.class.getName(), 1, Level.APPLICATIONERROR,
            "This Scheduler type is unknow", "This scheduler code is unknown", "The scheduler can't change", "Check the scheduler type");

    private static BEvent eventCantChangeScheduler = new BEvent(MilkSchedulerFactory.class.getName(), 2, Level.APPLICATIONERROR,
            "Scheduler doesn't change", "The scheduler can't change, due to an error", "The same scheduler is used", "Check previous error");

    private static BEvent eventSchedulerChanged = new BEvent(MilkSchedulerFactory.class.getName(), 3, Level.SUCCESS,
            "Scheduler change", "The scheduler change");

    private static BEvent eventNoScheduler = new BEvent(MilkSchedulerFactory.class.getName(), 4, Level.APPLICATIONERROR,
            "No Scheduler", "No scheduler are configured.", "No monitoring", "Select one");

    private static BEvent EVENT_HEART_BEAT = new BEvent(MilkSchedulerFactory.class.getName(), 5, Level.INFO,
            "Heat beat information", "Last heart beat, and next one");

    private MilkSchedulerInt currentScheduler = null;

    private Map<String, MilkSchedulerInt> setOfScheduler = new HashMap<String, MilkSchedulerInt>();

    private MilkSchedulerFactory() {
        setOfScheduler.put(TypeScheduler.QUARTZ.toString(), new MilkScheduleQuartz());
        setOfScheduler.put(TypeScheduler.THREADSLEEP.toString(), new MilkScheduleThreadSleep());
    }

    public static MilkSchedulerFactory getInstance() {
        return milkSchedulerFactory;
    }

    /**
     * verify that everything is correct for the scheduler environment
     * 
     * @param tenantId
     * @return
     */
    public List<BEvent> checkEnvironment(long tenantId) {
        List<BEvent> listEvents = new ArrayList<BEvent>();
        for (String key : setOfScheduler.keySet()) {
            MilkSchedulerInt scheduler = setOfScheduler.get(key);
            listEvents.addAll(scheduler.check(tenantId));
        }

        return listEvents;
    }

    /** read in the configuration the information */
    public List<BEvent> startup(long tenantId) {
        List<BEvent> listEvents = new ArrayList<BEvent>();
        if (currentScheduler == null) {
            synchronized (this) {
                // multi thread : check again, because now we are in the mono thread, and object may be created by an another thread
                if (currentScheduler == null) {
                    MilkSerializeProperties serializeProperties = new MilkSerializeProperties(tenantId);

                    SerializeOperation serializeOperation = serializeProperties.getCurrentScheduler();
                    listEvents.addAll(serializeOperation.listEvents);
                    String typeSchedulerSt = serializeOperation.valueSt;
                    currentScheduler = getFromType(typeSchedulerSt);

                    if (currentScheduler == null) {
                        // we force the SchedulerQuartz
                        MilkSchedulerInt quartz = new MilkScheduleQuartz();
                        currentScheduler = quartz;
                    }
                }
            }
        }
        return listEvents;
    }

    public TypeScheduler getTypeScheduler() {
        return currentScheduler.getType();
    }

    /**
     * @return
     */
    public List<String> getListTypeScheduler() {
        List<String> listSchedulers = new ArrayList<String>();
        for (String key : setOfScheduler.keySet()) {
            listSchedulers.add(key);
        }
        return listSchedulers;
    }

    /**
     * get the scheduler
     * 
     * @return
     */
    public MilkSchedulerInt getScheduler() {
        return currentScheduler;

    }

    /* ******************************************************************************** */
    /*                                                                                  */
    /* Information on the running */
    /*                                                                                  */
    /* ******************************************************************************** */
    /**
     * information on the execution
     * 
     * @param tenantId
     */
    public Date lastExecution = null;

    public void informRunInProgress(long tenantId) {
        lastExecution = new Date();
    }

    public StatusScheduler getStatus(long tenantId) {
        StatusScheduler statusScheduler;
        Date nextDateHeartBeat = null;
        if (currentScheduler != null) {
            statusScheduler = currentScheduler.getStatus(tenantId);
            nextDateHeartBeat = currentScheduler.getDateNextHeartBeat(tenantId);
        } else {
            statusScheduler = new StatusScheduler();
            statusScheduler.listEvents.add(eventNoScheduler);
        }
        if (!BEventFactory.isError(statusScheduler.listEvents)) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            String message = "Last Heart Beat[" + (lastExecution == null ? "undefined" : sdf.format(lastExecution)) + "]";
            if (statusScheduler.status == TypeStatus.STARTED)
                message += ", Next[" + (nextDateHeartBeat == null ? "undefined" : sdf.format(nextDateHeartBeat)) + "]";
            else
                message += "Scheduler stopped;";

            statusScheduler.listEvents.add(new BEvent(EVENT_HEART_BEAT, message));
        }
        return statusScheduler;

    }

    /* ******************************************************************************** */
    /*                                                                                  */
    /* Change the type of scheduler now */
    /*
     * /*
     */
    /* ******************************************************************************** */
    public List<BEvent> changeTypeScheduler(String typeSchedulerToChange, long tenantId) {
        List<BEvent> listEvents = new ArrayList<BEvent>();

        MilkSchedulerInt newScheduler = getFromType(typeSchedulerToChange);

        if (newScheduler == null) {
            listEvents.add(new BEvent(eventUnknowSchedulerType, "Scheduler[" + typeSchedulerToChange + "]"));
            return listEvents;
        }
        if (currentScheduler != null) {
            listEvents.addAll(currentScheduler.shutdown(tenantId));
        }
        if (BEventFactory.isError(listEvents)) {
            // can't change
            listEvents.add(new BEvent(eventCantChangeScheduler, "Scheduler[" + typeSchedulerToChange + "]"));
            return listEvents;
        }
        currentScheduler = newScheduler;
        listEvents.add(eventSchedulerChanged);

        listEvents.addAll(currentScheduler.startup(tenantId, true));

        // save the new value
        MilkSerializeProperties serializeProperties = new MilkSerializeProperties(tenantId);
        SerializeOperation serializeOperation = serializeProperties.saveCurrentScheduler(currentScheduler.getType().toString());
        listEvents.addAll(serializeOperation.listEvents);
        return listEvents;

    }

    /**
     * get the scheduler according the type, if it's exist, else return null
     * 
     * @param typeScheduler
     * @return
     */
    private MilkSchedulerInt getFromType(String typeScheduler) {
        if (setOfScheduler.containsKey(typeScheduler))
            return setOfScheduler.get(typeScheduler);
        return null;

    }
}
