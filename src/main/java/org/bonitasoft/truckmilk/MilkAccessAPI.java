package org.bonitasoft.truckmilk;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;


import org.bonitasoft.command.BonitaCommandDeployment.DeployStatus;
import org.bonitasoft.engine.api.CommandAPI;
import org.bonitasoft.engine.api.PlatformAPI;
import org.bonitasoft.engine.session.APISession;
import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEventFactory;
import org.bonitasoft.truckmilk.engine.MilkCmdControl;
import org.bonitasoft.truckmilk.engine.MilkCmdControlAPI;
import org.bonitasoft.truckmilk.engine.MilkConstantJson;
import org.bonitasoft.truckmilk.engine.MilkJobFactory;
import org.bonitasoft.truckmilk.engine.MilkPlugIn;
import org.bonitasoft.truckmilk.engine.MilkPlugInFactory;
import org.bonitasoft.truckmilk.engine.MilkPlugIn.PlugInMeasurement;
import org.bonitasoft.truckmilk.engine.MilkPlugIn.PlugInParameter;
import org.bonitasoft.truckmilk.job.MilkJob;
import org.bonitasoft.truckmilk.job.MilkJobContext;
import org.bonitasoft.truckmilk.toolbox.MilkLog;
import org.bonitasoft.truckmilk.toolbox.TypesCast;
import org.json.simple.JSONValue;



/**
 * this is the main access to the TruckMilk operation.
 * Truck Mil can be integrated in two different way:
 * - for the administration, to see all PlugIn available, create a Milk Tour, disable / enable,
 * change information on the Tour
 * - you develop a component (a page, a RestAPI...) and you want an execution of the component in
 * the TruckMilk word.
 * * develop your component as a MilkPlugIn
 * * register it via the MilkAccessAPI
 * How it's run ?
 * A TourJob is register in the timer. All Jobs contains a Detection (DetectionPlugIn) and an
 * operation (OperationPlugIn), with parameters
 * When the timer fire, TourJob call the TourCmd. This is a command deployed on the BonitaEngine.
 * The TourCmd will create the DetectionPlugIn, and runIt. THe DetectionPlugIn return a set of
 * DectectionItem
 * (example, the DetectinPlugIn is a MailPlug. It detect 3 mails, then return 3 DectectionItem)
 * The TourCmd call the OperationPlugIn for this 3 DetectinItem, and then register the execution.
 * On Return, TourJob register a new Timer.
 * Create your own DetectionPlugin or OperationPlugIn ? You then have a JAR file containing your own
 * detection / own operation.
 * Call the MilkAccessAPI.registerMyOperation and give a list of all detection / plug in class.
 * THe MilkAccessAPI create then a Command with all theses informations, plus the MilkAPi librairy.
 * It's all set ! You can then
 * register a Tour with your object.
 */
public class MilkAccessAPI {

    static MilkLog logger = MilkLog.getLogger(MilkAccessAPI.class.getName());

    public static String cstJsonListEvents = MilkCmdControl.CST_RESULT_LISTEVENTS;
    public static String cstJsonDeploimentsuc = "deploimentsuc";
    public static String cstJsonDeploimenterr = "deploimenterr";

    /**
     * Internal architecture
     * the TruckMilkAPI
     */
    public static MilkAccessAPI getInstance() {
        return new MilkAccessAPI();
    };

    public static class Parameter {

        public CommandAPI commandAPI;
        public PlatformAPI platFormAPI;
        public File pageDirectory;
        public APISession apiSession;

        public Map<String, Object> information;

        public static Parameter getInstanceFromJson(String jsonSt) {
            Parameter parameter = new Parameter();
            if (jsonSt == null)
                return parameter;

            parameter.setInformation(jsonSt);
            return parameter;
        }

        @SuppressWarnings("unchecked")
        public void setInformation(String jsonSt) {
            try {
                information = (Map<String, Object>) JSONValue.parse(jsonSt);
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String exceptionDetails = sw.toString();
                logger.severe("Parameter: ~~~~~~~~~~  : ERROR " + e + " at " + exceptionDetails);
                information = null;
            }
        }

        public void setApiSession(APISession apiSession) {
            this.apiSession = apiSession;
        }

        public long getTenantId() {
            return apiSession.getTenantId();
        }

    }

    public Map<String, Object> startup(Parameter parameter) {
        // first, deploy the command if needed
        List<BEvent> listEvents = new ArrayList<>();
        Map<String, Object> result = new HashMap<>();

        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        DeployStatus deployStatus = milkCmdControlAPI.checkAndDeployCommand(parameter.pageDirectory, parameter.commandAPI,
                parameter.platFormAPI, parameter.getTenantId());
        listEvents.addAll(deployStatus.listEvents);

        if (BEventFactory.isError(listEvents)) {
            result.put(cstJsonListEvents, BEventFactory.getHtml(listEvents));
            result.put(cstJsonDeploimenterr, "Error during deploiment");
            return result;
        }

        // // startup: check the environment   
        String statusDeployment = "";
        if (deployStatus.newDeployment)
            statusDeployment = "Command deployed with success;";
        else if (!deployStatus.alreadyDeployed)
            statusDeployment = "Command already deployed;";

        if (MilkConstantJson.cstEnvironmentStatus_V_ERROR.equals(result.get(MilkConstantJson.cstEnvironmentStatus))) {
            statusDeployment = "Bad environment;";
        } else
            //  second call the command getStatus		    
            result.putAll(milkCmdControlAPI.getStatus(parameter.commandAPI, parameter.getTenantId()));

        result.put(cstJsonDeploimentsuc, statusDeployment);
        return result;
    }

    /**
     * redeploy the command
     * 
     * @param parameter
     * @return
     */
    public Map<String, Object> commandReploy(Parameter parameter) {
        // first, deploy the command if needed
        List<BEvent> listEvents = new ArrayList<>();
        Map<String, Object> result = new HashMap<>();

        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        DeployStatus deployStatus = milkCmdControlAPI.forceDeployCommand(parameter.information, parameter.pageDirectory, parameter.commandAPI,
                parameter.platFormAPI, parameter.getTenantId());
        listEvents.addAll(deployStatus.listEvents);
        if (BEventFactory.isError(listEvents)) {
            result.put(cstJsonListEvents, BEventFactory.getHtml(listEvents));
            result.put(cstJsonDeploimenterr, "Error during deploiment");
            return result;
        }
        result.put(cstJsonDeploimentsuc, "Command redeployed;");
        return result;

    }

    public Map<String, Object> uninstall(Parameter parameter) {
        // first, deploy the command if needed
        List<BEvent> listEvents = new ArrayList<>();
        Map<String, Object> result = new HashMap<>();

        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        
        // stop the scheduler
        Map<String,Object> param = new HashMap<>();
        param.put("start", Boolean.FALSE);
        milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.SCHEDULERSTARTSTOP, param, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
        
        // Purge BonitaProperties

        // undeploy
        DeployStatus deployStatus = milkCmdControlAPI.unDeployCommand(parameter.information, parameter.pageDirectory, parameter.commandAPI,
                parameter.platFormAPI, parameter.getTenantId());
        listEvents.addAll(deployStatus.listEvents);
        if (BEventFactory.isError(listEvents)) {
            result.put(cstJsonListEvents, BEventFactory.getHtml(listEvents));
            result.put(cstJsonDeploimenterr, "Error during undeployment");
            return result;
        }
        
        
        result.put(cstJsonDeploimentsuc, "Command undeployed;");
        return result;

    }
    public Map<String, Object> getRefreshInformation(Parameter parameter) {
        // first, deploy the command if needed
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        // second call the command          
        return milkCmdControlAPI.getRefreshInformation(parameter.commandAPI, parameter.getTenantId());
    }

    public Map<String, Object> getStatusInformation(Parameter parameter) {
        // first, deploy the command if needed
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        // second call the command          
        return milkCmdControlAPI.getStatus(parameter.commandAPI, parameter.getTenantId());
    }

    public Map<String, Object> checkUpdateEnvironment(Parameter parameter) {
        // first, deploy the command if needed
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        // second call the command          
        return milkCmdControlAPI.checkUpdateEnvironment(parameter.commandAPI, parameter.getTenantId());
    }

    public Map<String, Object> addJob(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        return milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.ADDJOB, parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
    }

    public Map<String, Object> removeJob(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        return milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.REMOVEJOB, parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
    }

    public Map<String, Object> activateJob(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        return milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.ACTIVATEJOB, parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
    }

    public Map<String, Object> deactivateJob(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        return milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.DEACTIVATEJOB, parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
    }

    public Map<String, Object> abortJob(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        return milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.ABORTJOB, parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
    }

    public Map<String, Object> resetJob(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        return milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.RESETJOB, parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
    }

    
    
    public Map<String, Object> getParameters(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        return milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.GETPARAMETERS, parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
    }
    public Map<String, Object> getSavedExecution(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        return milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.GETSAVEDEXECUTION, parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
    }
    
    public Map<String, Object> getSavedExecutionDetail(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        return milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.GETSAVEDEXECUTIONDETAIL, parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
    }
    
    
    public Map<String, Object> getMeasurement(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        return milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.GETMEASUREMENT, parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
    }
    
    public Map<String, Object> threadDumpJob(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        return milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.THREADDUMPJOB, parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
    }
    
    
    public Map<String, Object> updateJob(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        return milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.UPDATEJOB, parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
    }

    public Map<String, Object> testButton(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        Map<String, Object> information = parameter.information;
        information.put(MilkCmdControl.CST_BUTTONNAME, parameter.information.get("buttonName"));
        return milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.TESTBUTTONARGS, parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
    }

    public Map<String, Object> immediateExecution(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        return milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.IMMEDIATEJOB, parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
    }

    /**
     * produce the Header
     * 
     * @param parameter
     * @return
     */
    public Map<String, String> readParameterHeader(Parameter parameter) {
        Map<String, String> mapHeaders = new HashMap<>();

        Long plugInTourId = TypesCast.getLong(parameter.information.get("plugintour"), 0L);
        long tenantId = parameter.apiSession.getTenantId();
        String paramname = TypesCast.getString(parameter.information.get("parametername"), null);
        MilkJobContext milkJobContext = new MilkJobContext( tenantId );

        MilkPlugInFactory milkPlugInFactory = MilkPlugInFactory.getInstance(milkJobContext);
        MilkJobFactory milkPlugInTourFactory = MilkJobFactory.getInstance(milkPlugInFactory);

        MilkJob milkPlugInTour = milkPlugInTourFactory.getJobById(plugInTourId);
        if (milkPlugInTour == null)
            logger.severe("Can't access plugInParameter[" + paramname + "]");
        else {
            // load the file then
            PlugInParameter plugInParameter = milkPlugInTour.getPlugIn().getDescription().getPlugInParameter(paramname);
            if (plugInParameter == null)
                logger.severe("Can't access plugInParameter[" + paramname + "] in Tour[" + milkPlugInTour.name + "]");
            else {
                mapHeaders.put("content-disposition", "attachment; filename=" + plugInParameter.fileName);
                mapHeaders.put("content-type", plugInParameter.contentType);
            }
        }
        return mapHeaders;
    }

    /**
     * read a parameter file, and send the result in the outputStream (send to the browser)
     * 
     * @param parameter
     * @param output
     * @return
     */
    public Map<String, String> readParameterContent(Parameter parameter, OutputStream output) {
        Map<String, String> mapHeaders = new HashMap<>();

        Long plugInTourId = TypesCast.getLong(parameter.information.get("plugintour"), 0L);
        long tenantId = parameter.apiSession.getTenantId();
        MilkJobContext milkJobContext = new MilkJobContext( tenantId );

        String paramname = TypesCast.getString(parameter.information.get("parametername"), null);

        MilkPlugInFactory milkPlugInFactory = MilkPlugInFactory.getInstance(milkJobContext);
        MilkJobFactory milkPlugInTourFactory = MilkJobFactory.getInstance(milkPlugInFactory);

        MilkJob milkPlugInTour = milkPlugInTourFactory.getJobById(plugInTourId);
        /*
         * MilkFactoryOp milkFactoryOp = milkPlugInTourFactory.dbLoadPlugInTour(plugInTourId);
         * if (milkFactoryOp.plugInTour==null)
         * logger.severe("## truckMilk: Can't access Tour by id=["+plugInTourId+"]" );
         * else
         * {
         */
        if (milkPlugInTour == null)
            logger.severe("Can't access plugInParameter[" + paramname + "]");
        else {
            // load the file then
            PlugInParameter plugInParameter = milkPlugInTour.getPlugIn().getDescription().getPlugInParameter(paramname);
            if (plugInParameter == null)
                logger.severe("Can't access plugInParameter[" + paramname + "] in Tour[" + milkPlugInTour.name + "]");
            else {
                milkPlugInTour.getParameterStream(plugInParameter, output);
                mapHeaders.put("content-disposition", "attachment; filename=" + plugInParameter.fileName);
                mapHeaders.put("content-type", plugInParameter.contentType);
            }
        }
        // response.addHeader("content-disposition", "attachment; filename=LogFiles.zip");
        // response.addHeader("content-type", "application/zip");
        return mapHeaders;
    }

    public Map<String, Object> scheduler(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        return milkCmdControlAPI.callJobOperation(MilkCmdControl.VERBE.SCHEDULERSTARTSTOP, parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.getTenantId());
    }

    public Map<String, Object> schedulerMaintenance(Parameter parameter) {
        MilkCmdControlAPI milkCmdControlAPI = MilkCmdControlAPI.getInstance();
        Map<String, Object> result = milkCmdControlAPI.schedulerMaintenance(parameter.information, parameter.pageDirectory, parameter.commandAPI, parameter.platFormAPI, parameter.getTenantId());
        return result;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getDocumentation(Parameter parameter, HttpServletResponse response) {
        Object result;
        long tenantId = parameter.apiSession.getTenantId();
        MilkJobContext milkJobContext = new MilkJobContext( tenantId );

        MilkPlugInFactory milkPlugInFactory = MilkPlugInFactory.getInstance(milkJobContext);

        if (parameter.information.get("pluginname") !=null) {
            // return for one plugin
            MilkPlugIn plugIn= milkPlugInFactory.getPluginFromName( (String) parameter.information.get("pluginname"));
            result = getDocumentationForAPlugin(  plugIn,parameter.pageDirectory);
        }
        else {
            // all plugin
            result= new ArrayList<Map<String,Object>>();
            for(MilkPlugIn milkPlugin : milkPlugInFactory.getListPlugIn()) {
                ((List)result).add(getDocumentationForAPlugin( milkPlugin,parameter.pageDirectory));
            }
            // sort the result per name
            Collections.sort( ((List)result), new Comparator<Map<String,Object>>()
               {
                 public int compare(Map<String,Object> s1,
                         Map<String,Object> s2)
                 {
                   return ((String)s1.get("name")).compareTo( (String) (s2.get("name")));
                 }
               });

        }
        
        response.addHeader("content-type", "text/html; charset=utf-8");
        try (OutputStream output = response.getOutputStream(); ) {
        
        String resultSt = JSONValue.toJSONString( result);
        output.write( resultSt.getBytes());
        
        output.flush();
        } catch (IOException e1) {
            logger.severe("Can't send documentation "+e1.getMessage());
        }
    }
    
    /**
     * 
     * @param plugIn
     * @param pageDirectory
     * @return
     */
    private Map<String,Object> getDocumentationForAPlugin(MilkPlugIn plugIn, File pageDirectory) {
        
        String fileNameSt = pageDirectory.getAbsolutePath()+"/resources/documentation/Milk"+plugIn.getName()+".html";
        File fileDocumentation = new File( fileNameSt );
        Map<String,Object> result = new HashMap<>();
        result.put("name", plugIn.getName() );
        result.put("label", plugIn.getDescription().getLabel() );
        result.put("category", plugIn.getDescription().getCategory().toString() );
        result.put("jobstopper", plugIn.getDescription().getJobCanBeStopped().toString() );
        result.put("explanation", plugIn.getDescription().getExplanation() );
        
        result.put("fulldocumentation", "<div class=\"alert alert-info\">Missing documentation</div>");
        if (fileDocumentation.exists()) { 
            try (FileInputStream fileInput = new FileInputStream(fileDocumentation)) {
                result.put("fulldocumentation", new String ( Files.readAllBytes( fileDocumentation.toPath() ) ));
            }catch(Exception e) {
                logger.severe("Can't read documentation ["+fileNameSt+"] "+e.getMessage());
            }
        }
        // Parameter
        StringBuffer parametersSt = new StringBuffer();
        parametersSt.append("<table class=\"table table-striped table-hover table-condensed\" style=\"width:100%\">");
        parametersSt.append("<tr><th>Name</th><th>Explanation</th></tr>");
        for (PlugInParameter parameter : plugIn.getDescription().getInputParameters())
        {
            parametersSt.append("<tr>");
            parametersSt.append("<td>"+parameter.label+"</td>");
            parametersSt.append("<td>");
            if (parameter.explanation!=null)
                parametersSt.append( parameter.explanation);
            if (parameter.explanation !=null && parameter.information !=null) 
                parametersSt.append("<p>");
            if (parameter.information !=null)
                parametersSt.append("<p>"+parameter.information);
            parametersSt.append("</td>");
            parametersSt.append("</tr>");
            
        }
        parametersSt.append("</table>");
        result.put("parameters",parametersSt.toString());

        StringBuffer measureSt = new StringBuffer();
        measureSt.append("<table class=\"table table-striped table-hover table-condensed\" style=\"width:100%\">");
        measureSt.append("<tr><th>Name</th><th>Explanation</th></tr>");
        for (PlugInMeasurement measure : plugIn.getDescription().getMapMesures().values()) {
            measureSt.append("<tr>");
            measureSt.append("<td>"+measure.label+"</td>");
            measureSt.append("<td>"+measure.explanation+"</td>");
        
        }
        measureSt.append("</table>");
        result.put("measures",measureSt.toString());

        return result;
  
    }
}
