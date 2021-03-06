import java.lang.management.RuntimeMXBean;
import java.lang.management.ManagementFactory;

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.logging.Logger;
import java.io.File
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.Runtime;

import org.json.simple.JSONObject;
import org.codehaus.groovy.tools.shell.CommandAlias;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;



import javax.naming.Context;
import javax.naming.InitialContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.Clob;
import java.util.Date;

import org.apache.commons.lang3.StringEscapeUtils

import org.bonitasoft.engine.identity.User;
import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.search.SearchResult;

import org.bonitasoft.web.extension.page.PageContext;
import org.bonitasoft.web.extension.page.PageController;
import org.bonitasoft.web.extension.page.PageResourceProvider;

import org.bonitasoft.engine.exception.AlreadyExistsException;
import org.bonitasoft.engine.exception.BonitaHomeNotSetException;
import org.bonitasoft.engine.exception.CreationException;
import org.bonitasoft.engine.exception.DeletionException;
import org.bonitasoft.engine.exception.ServerAPIException;
import org.bonitasoft.engine.exception.UnknownAPITypeException;
import org.bonitasoft.engine.bpm.process.ProcessDefinitionNotFoundException;
import org.bonitasoft.engine.bpm.process.ProcessDeploymentInfo;
import org.bonitasoft.engine.bpm.process.ProcessDeploymentInfoSearchDescriptor;
import org.bonitasoft.engine.search.SearchOptions;
import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.search.Order;
import org.bonitasoft.engine.api.TenantAPIAccessor;
import org.bonitasoft.engine.bpm.flownode.HumanTaskInstance;
import org.bonitasoft.engine.bpm.flownode.HumanTaskInstanceSearchDescriptor;
import org.bonitasoft.engine.bpm.process.ActivationState
import org.bonitasoft.engine.bpm.process.ArchivedProcessInstance;
import org.bonitasoft.engine.bpm.process.ArchivedProcessInstancesSearchDescriptor;
import org.bonitasoft.engine.bpm.process.ProcessInstance;
import org.bonitasoft.engine.bpm.process.ProcessInstanceSearchDescriptor;
import org.bonitasoft.engine.identity.UserSearchDescriptor;
import org.bonitasoft.engine.session.APISession;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.api.IdentityAPI;

import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEventFactory;
import org.bonitasoft.log.event.BEvent.Level;

import org.bonitasoft.truckmilk.MilkAccessAPI;
import org.bonitasoft.truckmilk.MilkAccessAPI.Parameter;
import org.bonitasoft.truckmilk.engine.MilkPlugIn.PlugInParameter.FilterProcess


public class Actions {

    private static Logger logger= Logger.getLogger("org.bonitasoft.custompage.truckmilk.groovy");




    // 2018-03-08T00:19:15.04Z
    public final static SimpleDateFormat sdfJson = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    public final static SimpleDateFormat sdfHuman = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /* -------------------------------------------------------------------- */
    /*                                                                      */
    /* doAction */
    /*                                                                      */
    /* -------------------------------------------------------------------- */

    public static Index.ActionAnswer doAction(HttpServletRequest request, String paramJsonSt, HttpServletResponse response, PageResourceProvider pageResourceProvider, PageContext pageContext) {

        // logger.fine("#### cockpit:Actions start");
        Index.ActionAnswer actionAnswer = new Index.ActionAnswer();
        List<BEvent> listEvents=new ArrayList<BEvent>();


        try {
            String action=request.getParameter("action");

            if (action==null || action.length()==0 )
            {
                actionAnswer.isManaged=false;
                // logger.fine("#### log:Actions END No Actions");
                return actionAnswer;
            }
            actionAnswer.isManaged=true;

            APISession apiSession = pageContext.getApiSession();
            HttpSession httpSession = request.getSession();
            ProcessAPI processAPI = TenantAPIAccessor.getProcessAPI(apiSession);
            IdentityAPI identityAPI = TenantAPIAccessor.getIdentityAPI(apiSession);

            Parameter parameter = Parameter.getInstanceFromJson(paramJsonSt);

            parameter.apiSession=apiSession;
            parameter.commandAPI = TenantAPIAccessor.getCommandAPI( apiSession );
            parameter.platFormAPI= null; // TenantAPIAccessor.getPlatformAPI( apiSession );
            parameter.pageDirectory = pageResourceProvider.getPageDirectory();
            MilkAccessAPI milkAccessAPI = MilkAccessAPI.getInstance();

            // logger.fine("#### log:Actions_2 ["+action+"]");
            if ("startup".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                actionAnswer.responseMap = milkAccessAPI.startup( parameter);
            }
            else if ("refresh".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                actionAnswer.responseMap = milkAccessAPI.getRefreshInformation( parameter);
            }
            else if ("getstatus".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                actionAnswer.responseMap = milkAccessAPI.getStatusInformation( parameter);
            }
            else if ("addJob".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                parameter.information.put("userId", apiSession.getUserId());
                actionAnswer.responseMap = milkAccessAPI.addJob( parameter);
            }
            else if ("removeJob".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                parameter.information.put("userId", apiSession.getUserId());
                actionAnswer.responseMap = milkAccessAPI.removeJob( parameter);
            }
            else if ("activateJob".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                parameter.information.put("userId", apiSession.getUserId());
                actionAnswer.responseMap = milkAccessAPI.activateJob( parameter);
            }
            else if ("deactivateJob".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                parameter.information.put("userId", apiSession.getUserId());
                actionAnswer.responseMap = milkAccessAPI.deactivateJob( parameter);
            }
            else if ("abortJob".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                parameter.information.put("userId", apiSession.getUserId());
                actionAnswer.responseMap = milkAccessAPI.abortJob( parameter);
            }
            else if ("resetJob".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                parameter.information.put("userId", apiSession.getUserId());
                actionAnswer.responseMap = milkAccessAPI.resetJob( parameter);
            }

            else if ("getParameters".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                parameter.information.put("userId", apiSession.getUserId());
                actionAnswer.responseMap = milkAccessAPI.getParameters( parameter);
            }
            else if ("getSavedExecution".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                parameter.information.put("userId", apiSession.getUserId());
                actionAnswer.responseMap = milkAccessAPI.getSavedExecution( parameter);
            }
            else if ("getSavedExecutionDetail".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                parameter.information.put("userId", apiSession.getUserId());
                actionAnswer.responseMap = milkAccessAPI.getSavedExecutionDetail( parameter);
            }
            else if ("getMeasurement".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                parameter.information.put("userId", apiSession.getUserId());
                actionAnswer.responseMap = milkAccessAPI.getMeasurement( parameter);
            }

            else if ("threadDumpJob".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                parameter.information.put("userId", apiSession.getUserId());
                actionAnswer.responseMap = milkAccessAPI.threadDumpJob( parameter);
            }
            else if ("collect_reset".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                String paramJsonPartial = request.getParameter("paramjsonpartial");
                if (paramJsonPartial==null)
                    paramJsonPartial="";
                // logger.fine("collect_reset  paramJsonPartial=["+paramJsonPartial+"]");
                httpSession.setAttribute("accumulate", paramJsonPartial );
                actionAnswer.responseMap.put("status", "ok");
            }
            else if ("collect_add".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                String paramJsonPartial = request.getParameter("paramjsonpartial");
                // logger.fine("collect_add paramJsonPartial=["+paramJsonPartial+"] json=["+paramJsonSt+"]");

                String accumulateJson = (String) httpSession.getAttribute("accumulate" );
                accumulateJson+=paramJsonPartial;
                httpSession.setAttribute("accumulate", accumulateJson );
                actionAnswer.responseMap.put("status", "ok");
            }
            else if ("updateJob".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                String accumulateJson = (String) httpSession.getAttribute("accumulate" );
                // logger.fine("update Job accumulateJson=["+accumulateJson+"]");
                if (accumulateJson !=null && accumulateJson.length() >0)
                {
                    // logger.fine("collect_end use the saved value ["+accumulateJson+"]");
                    // recalculate the parameters from accumate mechanism
                    parameter.setInformation(accumulateJson);
                }
                httpSession.setAttribute("accumulate", "" );
                parameter.information.put("userId", apiSession.getUserId());
                actionAnswer.responseMap = milkAccessAPI.updateJob( parameter);
            }
            else if ("testButton".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                String accumulateJson = (String) httpSession.getAttribute("accumulate" );
                // logger.fine("update Job accumulateJson=["+accumulateJson+"]");
                if (accumulateJson !=null && accumulateJson.length() >0)
                {
                    // logger.fine("collect_end use the saved value ["+accumulateJson+"]");
                    // recalculate the parameters from accumate mechanism
                    parameter.setInformation(accumulateJson);
                }
                httpSession.setAttribute("accumulate", "" );
                actionAnswer.responseMap = milkAccessAPI.testButton( parameter);
            }
            else if ("immediateExecution".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                parameter.information.put("userId", apiSession.getUserId());
                actionAnswer.responseMap = milkAccessAPI.immediateExecution( parameter);
                // logger.fine("#### TruckMilk:Actions call immediateExecution : YES");
            }
            else if ("downloadParamFile".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                actionAnswer.isManaged=true;
                actionAnswer.isResponseMap=false;

                // ATTENTION : on a Linux Tomcat, order is important : first, HEADER then CONTENT. on Windows Tomcat, don't care
                // logger.fine("#### TruckMilk:Actions downloadParamFile write To Output=["+parameter.toString()+"]");

                String logHeaders="";
                Map<String,String> mapHeaders = milkAccessAPI.readParameterHeader( parameter );
                for (String key  : mapHeaders.keySet())
                {
                    response.addHeader( key, mapHeaders.get( key ));
                    logHeaders += key+"="+mapHeaders.get( key )+"; ";
                }

                OutputStream output = response.getOutputStream();


                milkAccessAPI.readParameterContent(parameter, output );
                /*
                 */
                // response.addHeader("content-disposition", "attachment; filename=LogFiles.zip");
                // response.addHeader("content-type", "application/zip");
                output.flush();
                output.close();

            }
            else if ("uploadParamFile".equals(action))
            {
                // logger.fine("#### TruckMilk:Actions call immediateExecution");

                actionAnswer.responseMap = milkAccessAPI.updateJob( parameter);
                // logger.fine("#### TruckMilk:Actions call immediateExecution : YES");

            }
            else  if ("scheduler".equals(action))
            {
           //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer; 
                }
                actionAnswer.responseMap = milkAccessAPI.scheduler( parameter);
            }

            else  if ("commandredeploy".equals(action))
            {
           //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer; 
                }
                actionAnswer.responseMap = milkAccessAPI.commandReploy( parameter);
            }
            else  if ("uninstall".equals(action))
            {
           //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer; 
                }
                actionAnswer.responseMap = milkAccessAPI.uninstall( parameter);
            }
            else  if ("schedulermaintenance".equals(action))
            {
           //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer; 
                }
                actionAnswer.responseMap = milkAccessAPI.schedulerMaintenance( parameter);
            }
            else if ("queryprocess".equals(action))
            {
           //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer; 
                }
                List listProcesses = new ArrayList();
                Object jsonParam = (paramJsonSt==null ? null : JSONValue.parse(paramJsonSt));
                String processFilter = (jsonParam == null ? "" : jsonParam.get("userfilter"));
                String filterProcessSt = (jsonParam == null ? "" : jsonParam.get("filterProcess"));
                SearchOptionsBuilder searchOptionsBuilder = new SearchOptionsBuilder(0,20);
                // searchOptionsBuilder.greaterOrEquals(ProcessDeploymentInfoSearchDescriptor.NAME, processFilter);
                // searchOptionsBuilder.lessOrEquals(ProcessDeploymentInfoSearchDescriptor.NAME, processFilter+"z");
                searchOptionsBuilder.searchTerm(processFilter);

                if (FilterProcess.ONLYDISABLED.toString().equals(filterProcessSt))
                {
                    searchOptionsBuilder.filter(ProcessDeploymentInfoSearchDescriptor.ACTIVATION_STATE, ActivationState.DISABLED.toString());
                }
                if (FilterProcess.ONLYENABLED.toString().equals(filterProcessSt))
                {
                    searchOptionsBuilder.filter(ProcessDeploymentInfoSearchDescriptor.ACTIVATION_STATE, ActivationState.ENABLED.toString());
                }

                searchOptionsBuilder.sort( ProcessDeploymentInfoSearchDescriptor.NAME,  Order.ASC);
                searchOptionsBuilder.sort( ProcessDeploymentInfoSearchDescriptor.VERSION,  Order.ASC);
                Set<String> setProcessesWithoutVersion=new HashSet<String>();

                SearchResult<ProcessDeploymentInfo> searchResult = processAPI.searchProcessDeploymentInfos(searchOptionsBuilder.done() );
                // logger.info("TruckMilk:Search process deployment containing ["+processFilter+"] - found "+searchResult.getCount());

                for (final ProcessDeploymentInfo processDeploymentInfo : searchResult.getResult())
                {
                    final Map<String, Object> oneRecord = new HashMap<String, Object>();
                    oneRecord.put("display", processDeploymentInfo.getName() + " (" + processDeploymentInfo.getVersion()+")");
                    oneRecord.put("id", processDeploymentInfo.getName() + " (" + processDeploymentInfo.getVersion()+")");
                    listProcesses.add( oneRecord );
                    setProcessesWithoutVersion.add(processDeploymentInfo.getName());
                }
                // add all processes without version
                for (String processName : setProcessesWithoutVersion ) {
                    final Map<String, Object> oneRecord = new HashMap<String, Object>();
                    oneRecord.put("display", processName);
                    oneRecord.put("id", processName);
                    listProcesses.add( oneRecord );
                }
                // sort the result again to have the "process without version" at the correct place

                Collections.sort(listProcesses, new Comparator< Map<String, Object>>()
                        {
                            public int compare( Map<String, Object> s1,
                                    Map<String, Object> s2)
                            {
                                return ((String)s1.get("display")).compareTo( ((String)s2.get("display")));
                            }
                        });
                actionAnswer.responseMap.put("listProcess", listProcesses);
                actionAnswer.responseMap.put("nbProcess", searchResult.getCount());
            }
            else if ("queryusers".equals(action))
            {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                List listUsers = new ArrayList();
                final SearchOptionsBuilder searchOptionBuilder = new SearchOptionsBuilder(0, 20);
                // http://documentation.bonitasoft.com/?page=using-list-and-search-methods
                searchOptionBuilder.filter(UserSearchDescriptor.ENABLED, Boolean.TRUE);
                Object jsonParam = (paramJsonSt==null ? null : JSONValue.parse(paramJsonSt));
                searchOptionBuilder.searchTerm( jsonParam==null ? "" : jsonParam.get("userfilter") );

                searchOptionBuilder.sort(UserSearchDescriptor.LAST_NAME, Order.ASC);
                searchOptionBuilder.sort(UserSearchDescriptor.FIRST_NAME, Order.ASC);
                final SearchResult<User> searchResult = identityAPI.searchUsers(searchOptionBuilder.done());
                for (final User user : searchResult.getResult())
                {
                    final Map<String, Object> oneRecord = new HashMap<String, Object>();
                    // oneRecord.put("display", user.getFirstName()+" " + user.getLastName()  + " (" + user.getUserName() + ")");
                    oneRecord.put("display", user.getLastName() + "," + user.getFirstName() + " (" + user.getUserName() + ")");
                    oneRecord.put("id", user.getId());
                    listUsers.add( oneRecord );
                }
                actionAnswer.responseMap.put("listUsers", listUsers);
                actionAnswer.responseMap.put("nbUsers", searchResult.getCount());


            } else if ("getdocumentation".equals(action)) {
                //Make sure no action is executed if the CSRF protection is active and the request header is invalid
                if (! TokenValidator.checkCSRFToken(request, response)) {
                    actionAnswer.isResponseMap=false;
                    return actionAnswer;
                }

                milkAccessAPI.getDocumentation(parameter, response );
                actionAnswer.isResponseMap=false;
                
            }
            logger.fine("#### TruckMilk:Actions END responseMap.size()="+actionAnswer.responseMap.size());
            return actionAnswer;
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionDetails = sw.toString();
            logger.severe("#### TruckMilk:Groovy Exception ["+e.toString()+"] at "+exceptionDetails);
            actionAnswer.isResponseMap=true;
            actionAnswer.responseMap.put("Error", "log:Groovy Exception ["+e.toString()+"] at "+exceptionDetails);



            return actionAnswer;
        }
    }





}
