package workflow.business.service.impl;

import auth.discard.model.SysDepartTreeModel;
import auth.domain.common.dto.UserDepartDto;
import auth.domain.common.service.AuthInfo;
import com.baomidou.dynamic.datasource.annotation.DS;
import workflow.business.service.*;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.*;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.history.*;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.repository.ProcessDefinitionQuery;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.runtime.ProcessInstanceQuery;
import org.activiti.engine.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import workflow.common.constant.ActivitiConstant;
import workflow.common.error.WorkFlowException;
import workflow.common.redis.JedisMgr_wf;
import workflow.common.utils.CheckDataUtil;
import workflow.common.utils.UUIDUtil;
import workflow.business.model.*;
import workflow.business.model.entity.EditModelEntity;
import workflow.business.model.entity.ReleaseProcessEntity;
import workflow.business.model.entity.UnreleaseProcessEntity;
import workflow.business.model.entity.UserTaskFinishedEntity;
import workflow.olddata.core.exception.GraphQLException;
import workflow.ide.A2CParser;
import workflow.ide.C2AParser;
import workflow.ide.core.BpmnProcess;
import workflow.ide.core.Definitions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.Map.Entry;


/**
 * @ClassName: ConsoleServiceImpl   ???GraphQL??????
 * @Description: ?????????????????????
 * @author KaminanGTO
 * @date 2018???11???7??? ??????3:10:28
 *
 * ????????? GraphQL??????
 */
@Service("ConsoleService")
@DS("master")
public class ConsoleServiceImpl implements ConsoleService {

	private Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private TaskService taskService;

	@Autowired
	private FormService formService;

	@Autowired
	private RuntimeService runtimeService;

	@Autowired
	private RepositoryService repositoryService;

	@Autowired
	private HistoryService historyService;

	@Autowired
	private EditModelService editModelService;

	@Autowired
	private ReleaseProcessService releaseProcessService;

	@Autowired
	private UnreleaseProcessService unreleaseProcessService;

	@Autowired
	private UserTaskFinishedService userTaskFinishedService;

	@Autowired
	private WorkflowService workFlowService;

	@Autowired
	private JedisMgr_wf jedisMgrWf;

	@Autowired
	private ProcessTaskService processTaskService;

	@Autowired
	private AuthInfo authInfoUtil;//????????????

	/*@Autowired
	private MongoTemplate mongoTemplate;*/

	/**
	 * @ClassName: EShowVersionType
	 * @Description:????????????????????????????????????
	 * @author KaminanGTO
	 * @date 2018???11???8??? ??????11:04:28
	 *
	 */
	enum EShowVersionType
	{
		/**
		 * @Fields All : ????????????
		 */
		All,
		/**
		 * @Fields Has : ???????????????????????????
		 */
		Has,
		/**
		 * @Fields NotHas : ???????????????????????????
		 */
		NotHas
	}

	/**
	 * @ClassName: EOrderType
	 * @Description: ????????????
	 * @author KaminanGTO
	 * @date 2018???11???8??? ??????3:12:58
	 *
	 */
	enum EOrderType
	{
		/**
		 * @Fields None : ?????????
		 */
		None,
		/**
		 * @Fields Desc : ??????
		 */
		Desc,
		/**
		 * @Fields Asc : ??????
		 */
		Asc
	}

	/**
	 * @ClassName: EProcessType
	 * @Description: ??????????????????
	 * @author KaminanGTO
	 * @date 2018???11???9??? ??????3:04:30
	 *
	 */
	enum EProcessType
	{
		/**
		 * @Fields Main : ?????????
		 */
		Main(1),
		/**
		 * @Fields Sub : ?????????
		 */
		Sub(2);

		public int value;

		EProcessType(int value)
		{
			this.value = value;
		}
	}

	/**
	 * @ClassName: EProcessDefState
	 * @Description: ??????????????????
	 * @author KaminanGTO
	 * @date 2018???11???9??? ??????3:53:02
	 *
	 */
	enum EProcessDefState
	{
		/**
		 * @Fields All : ????????????
		 */
		All,
		/**
		 * @Fields Main : ?????????
		 */
		Active,
		/**
		 * @Fields Sub : ?????????
		 */
		Suspend
	}

	/**
	 * @ClassName: EProcessInstanceState
	 * @Description: ??????????????????
	 * @author KaminanGTO
	 * @date 2018???11???9??? ??????5:39:11
	 *
	 */
	enum EProcessInstanceState
	{

		/**
		 * @Fields All : ??????
		 */
		All(-1),
		/**
		 * @Fields Suspend : ?????????
		 */
		Suspend(0),
		/**
		 * @Fields Active : ?????????
		 */
		Active(1),
		/**
		 * @Fields Finish : ?????????
		 */
		Finish(2),
		/**
		 * @Fields Delete : ????????????????????????
		 */
		Delete(3);

		public int value;

		EProcessInstanceState(int value)
		{
			this.value = value;
		}

		static EProcessInstanceState valueOf(int value)
		{
			EProcessInstanceState state = EProcessInstanceState.All;
			if(value > -1 && value < EProcessInstanceState.values().length-1)
			{
				state = EProcessInstanceState.values()[value+1];
			}
			return state;
		}
	}

	/**
	 * @ClassName: EProcessTaskState
	 * @Description: ??????????????????
	 * @author KaminanGTO
	 * @date 2018???11???27??? ??????4:24:53
	 *
	 */
	enum EProcessTaskState
	{

		/**
		 * @Fields All : ??????
		 */
		All(-1),
		/**
		 * @Fields Suspend : ?????????
		 */
		Suspend(0),
		/**
		 * @Fields Active : ?????????
		 */
		Active(1),
		/**
		 * @Fields Finish : ?????????
		 */
		Finish(2),
		/**
		 * @Fields Delete : ????????????????????????
		 */
		Delete(3);

		public int value;

		EProcessTaskState(int value)
		{
			this.value = value;
		}

		static EProcessTaskState valueOf(int value)
		{
			EProcessTaskState state = EProcessTaskState.All;
			if(value > -1 && value < EProcessTaskState.values().length-1)
			{
				state = EProcessTaskState.values()[value+1];
			}
			return state;
		}
	}

	/**
	 * @Title: makeOrderStr
	 * @Description: ?????????????????????????????????????????????null
	 * @param key
	 * @param type
	 * @return  ????????????
	 * @return String    ????????????
	 *
	 */
	private String makeOrderStr(String key, int type)
	{
		EOrderType orderType = getOrderType(type);
		switch(orderType)
		{
			case Asc:
				return key + " asc";
			case Desc:
				return key + " desc";
			default:
				return null;
		}
	}

	/**
	 * @Title: getOrderType
	 * @Description: ????????????????????????
	 * @param type
	 * @return  ????????????
	 * @return EOrderType    ????????????
	 *
	 */
	private EOrderType getOrderType(int type)
	{
		EOrderType orderType = EOrderType.None;
		if(type > 0 && type < EOrderType.values().length)
		{
			orderType = EOrderType.values()[type];
		}
		return orderType;
	}


	/**
	 * @Title: init
	 * @Description: ???????????????
	 * @return void    ????????????
	 *
	 */
	@Autowired
	private void init(JedisMgr_wf jedisMgrWf)
	{
		logger.debug("------------- init console service");
		initFinishedInfo();
	}

	/**
	 * @Title: initFinishedInfo
	 * @Description: ????????????????????????????????????
	 * @return void    ????????????
	 *
	 */
	private void initFinishedInfo()
	{
		logger.debug("------------- init finishedinfo");
		//???????????????????????????
		if(jedisMgrWf.hasKey(getUserFinishedKey()))
		{
			return;
		}
		/*if(mongoTemplate.findAll(MongoEntity.class, getUserFinishedKey())!=null && mongoTemplate.findAll(MongoEntity.class, getUserFinishedKey()).size()>0){
			//????????????????????????????????????
			return;
		}*/
		//?????????redis???????????????????????????????????????????????????
		if(jedisMgrWf.hasKey(getLoadingKey()))
		{
			return;
		}
		/*if(mongoTemplate.findAll(MongoEntity.class, getLoadingKey())==null && mongoTemplate.findAll(MongoEntity.class, getLoadingKey()).size()==0){
			//????????????????????????????????????
			return;
		}*/
		logger.debug("------------- start init finishedinfo");
		//??????????????????????????????????????????
		/*MongoEntity mongoEntity=new MongoEntity();
		mongoEntity.setLoading("workflow finished data init");
		mongoTemplate.save(mongoEntity,getLoadingKey());*/
		//timeout(300);
		//mongoTemplate.remove(mongoEntity, getLoadingKey());

		jedisMgrWf.setValue(getLoadingKey(), "workflow finished data init", ActivitiConstant.REDIS_FINISHED_INIT_TIME);
		//???????????????
		// ???mysql??????????????????
		List<UserTaskFinishedEntity> finishList = userTaskFinishedService.simpleList();
		if(finishList != null && !finishList.isEmpty())
		{
			//??????????????????
			Map<String, Double> userFinishMap = new HashMap<String, Double>();
			Map<String, Double> unitFinishMap = new HashMap<String, Double>();
			for(UserTaskFinishedEntity entity : finishList)
			{
				String userId = entity.getUserId();
				String unitId = entity.getUnitId();
				if(!userFinishMap.containsKey(userId))
				{
					userFinishMap.put(userId, 1d);
				}
				else
				{
					userFinishMap.put(userId, userFinishMap.get(userId) + 1);
				}
				if(unitId != null)
				{
					if(!unitFinishMap.containsKey(unitId))
					{
						unitFinishMap.put(unitId, 1d);
					}
					else
					{
						unitFinishMap.put(unitId, unitFinishMap.get(unitId) + 1);
					}
				}
			}
			//??????????????????
			Iterator<Entry<String, Double>> iter = userFinishMap.entrySet().iterator();
			while(iter.hasNext())
			{
				Entry<String, Double> entry = iter.next();
				/*mongoEntity.setLoading(null);
				mongoEntity.setUnitId(null);
				mongoEntity.setUserId(entry.getKey());
				mongoTemplate.save(mongoEntity,getUserFinishedKey());*/
				jedisMgrWf.pushIncrSortSet(getUserFinishedKey(), entry.getKey(), entry.getValue());
			}
			iter = unitFinishMap.entrySet().iterator();
			while(iter.hasNext())
			{
				Entry<String, Double> entry = iter.next();
				/*mongoEntity.setUserId(null);
				mongoEntity.setLoading(null);
				mongoEntity.setUnitId(entry.getKey());
				mongoTemplate.save(mongoEntity,getUnitFinishedKey());*/
				jedisMgrWf.pushIncrSortSet(getUnitFinishedKey(), entry.getKey(), entry.getValue());
			}
		}
		//???????????????
		jedisMgrWf.delete(getLoadingKey());
		/*mongoEntity.setLoading("workflow finished data init");
		mongoTemplate.remove(mongoEntity, getLoadingKey());*/
		logger.debug("------------- end init finishedinfo");
	}

	/* (non-Javadoc)
	 * @see zdit.zdboot.workflow.console.service.ConsoleService#getURProcessList(java.lang.String, java.lang.String, int, int, int, int, int)
	 */
	@Override
	public PageUnReleaseProcessList getURProcessList(String key, String name, int showVersion, int pageNum,
														int pageSize, int orderByCreateTime, int orderByUpdateTime) {
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}

		Map<String, Object> params = new HashMap<String, Object>();;

		//???????????????
		params.put("process_type", EProcessType.Main.value);

		//??????key
		if(CheckDataUtil.isNotNull(key))
		{
			params.put("process_key", key);
		}
		//????????????
		if(CheckDataUtil.isNotNull(name))
		{
			params.put("process_name", name);
		}
		EShowVersionType showVersionType  = EShowVersionType.All;
		if(showVersion > 0 && showVersion < EShowVersionType.values().length)
		{
			showVersionType = EShowVersionType.values()[showVersion];
		}
//		System.out.println("===================showVersionType:"+showVersionType);
		//????????????????????????
		switch(showVersionType)
		{
			case All:
				break;
			case Has:
				params.put("versionJudge", "1");
				break;
			case NotHas:
				params.put("versionJudge", "0");
				break;
			default:
				break;
		}
		//??????
		String order = makeOrderStr("create_time" , orderByCreateTime);
		if(order == null)
		{
			order = makeOrderStr("update_time" , orderByUpdateTime);
		}
		if(order != null)
		{
			params.put("orderInfo", order);
		}
//		System.out.println("orderInfo:"+params.get("orderInfo"));
		//????????????
		params.put("pageNumber", pageNum);
		params.put("pageSize", pageSize);
		Page<EditModelEntity> page = editModelService.listStcsmEditModel(params);
		PageUnReleaseProcessList pageList = new PageUnReleaseProcessList();

		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal((int) page.getTotal());
//		pageList.setNowPage(pageNum);
		List<EditModelEntity> editModels = page.getRecords();
		List<UnReleaseProcessData> list = new ArrayList<UnReleaseProcessData>();
		for(EditModelEntity editModel : editModels)
		{
			UnReleaseProcessData data = new UnReleaseProcessData();
			data.setId(editModel.getProcessId());
			data.setKey(editModel.getProcessKey());
			data.setName(editModel.getProcessName());
			data.setDescription(editModel.getDescription());
			data.setCreateTime(editModel.getCreateTime());
			data.setUpdateTime(editModel.getUpdateTime());
			data.setReleaseVersion(editModel.getReleaseVersion());
			list.add(data);
		}
		pageList.setRows(list);
		return pageList;
	}

	@Override
	public UnReleaseProcessData getURProcess(String id) throws WorkFlowException {
//		System.out.println("===============getURProcess=============");
		if ("0".equals(id)) {
			return newUndeployedModel();
		}

		CheckDataUtil.checkNull(id, "id");
		EditModelEntity editModel = editModelService.getStcsmEditModelById(id);
		if (editModel == null) {
			throw new GraphQLException(602);
		}

//		ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
//				.processDefinitionKey(editModel.getProcessKey()).latestVersion().singleResult();
//
//		int version = 0;
//		if (processDefinition != null) {
//			version = processDefinition.getVersion();
//		}
		UnReleaseProcessData data = new UnReleaseProcessData();
		data.setId(editModel.getProcessId());
		data.setKey(editModel.getProcessKey());
		data.setName(editModel.getProcessName());
		data.setDescription(editModel.getDescription());
		data.setCreateTime(editModel.getCreateTime());
		data.setUpdateTime(editModel.getUpdateTime());
		//data.setReleaseVersion(editModel.getReleaseVersion());
		data.setInfo(editModel.getInfo());
		data.setThumbnail(editModel.getThumbnail());
		//data.setReleaseVersion(version);
		data.setReleaseVersion(editModel.getReleaseVersion());
		data.setProcessType(editModel.getProcessType());

		String subProcessStr = subProcessStr();
//		System.out.println("===============subProcessStr:"+subProcessStr);

//		String ideinfo=A2CParser.convert(editModel.getInfo(), editModel.getProcessType(), subProcessStr, false);
//		System.out.println("===============ideinfo:"+ideinfo);

		data.setIdeInfo(A2CParser.convert(editModel.getInfo(), editModel.getProcessType(), subProcessStr, false));
//		System.out.println("===============333333333333333333333=============");
		return data;
	}


	/**
	 * ?????????????????????
	 * @return
	 */
	public UnReleaseProcessData newUndeployedModel() {
		UnReleaseProcessData data = new UnReleaseProcessData();

		data.setId("");
		data.setKey("");
		data.setName("");
		data.setDescription("");
		data.setCreateTime(new Date());
		data.setUpdateTime(new Date());
		data.setReleaseVersion(0);
		data.setInfo("");
		data.setThumbnail("");
		//data.setReleaseVersion(editModel.getReleaseVersion());
		data.setProcessType(1);

		String defaultString = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
				"\n" +
				"<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:activiti=\"http://activiti.org/bpmn\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" xmlns:omgdc=\"http://www.omg.org/spec/DD/20100524/DC\" xmlns:omgdi=\"http://www.omg.org/spec/DD/20100524/DI\" typeLanguage=\"http://www.w3.org/2001/XMLSchema\" expressionLanguage=\"http://www.w3.org/1999/XPath\" targetNamespace=\"http://bpmn.io/schema/bpmn\">\n" +
				"  <process id=\"Process_\" name=\"??????_\" isExecutable=\"false\">\n" +
				"    <startEvent id=\"StartEvent_1\"/>\n" +
				"  </process>\n" +
				"  <bpmndi:BPMNDiagram id=\"BPMNDiagram_1\">  \n" +
				"    <bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"SubProcess_1\"> \n" +
				"      <bpmndi:BPMNShape id=\"_BPMNShape_StartEvent_2\" bpmnElement=\"StartEvent_1\"> \n" +
				"        <dc:Bounds xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" x=\"280\" y=\"118\" width=\"36\" height=\"36\"></dc:Bounds> \n" +
				"      </bpmndi:BPMNShape>  \n" +
				"      <bpmndi:BPMNShape id=\"CallActivity_0xgwxvt_di\"> \n" +
				"        <dc:Bounds xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" x=\"223\" y=\"201\" width=\"100\" height=\"80\"></dc:Bounds> \n" +
				"      </bpmndi:BPMNShape>  \n" +
				"      <bpmndi:BPMNShape id=\"CallActivity_1oqj16h_di\" bpmnElement=\"Task_1mz3bdj\"> \n" +
				"        <dc:Bounds xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" x=\"177\" y=\"248\" width=\"100\" height=\"80\"></dc:Bounds> \n" +
				"      </bpmndi:BPMNShape> \n" +
				"    </bpmndi:BPMNPlane> \n" +
				"  </bpmndi:BPMNDiagram>\n" +
				"</definitions>";

		String subProcessStr = subProcessStr();
		data.setIdeInfo(A2CParser.convert(defaultString, 1, subProcessStr, true));
		return data;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private String subProcessStr() {

		PageUnReleaseSubProcessList pageSubProcessDefList = getURSubProcessList(null,null,0, 1, Integer.MAX_VALUE, 0, 0);

		List<Map> toMap = new ArrayList<>();
		Map mpBlank = new HashMap();
		mpBlank.put("value", "");
		mpBlank.put("name", "");
		toMap.add(mpBlank);
		for (UnReleaseSubProcessData subProcessData : pageSubProcessDefList.getRows()) {
			Map mp = new HashMap();
			mp.put("value", subProcessData.getKey());
			mp.put("name", subProcessData.getName());
			toMap.add(mp);
		}
		String json = JSON.toJSONString(toMap);
		return json;
	}

	@Override
	public UnReleaseProcessData getURProcessByKey(String key) throws WorkFlowException {
		CheckDataUtil.checkNull(key, "key");
		EditModelEntity editModel = editModelService.getStcsmEditModelByKey(key);
		if(editModel == null ){
			return  null;
		}
		UnReleaseProcessData data = new UnReleaseProcessData();
		data.setId(editModel.getProcessId());
		data.setKey(editModel.getProcessKey());
		data.setName(editModel.getProcessName());
		data.setDescription(editModel.getDescription());
		data.setCreateTime(editModel.getCreateTime());
		data.setUpdateTime(editModel.getUpdateTime());
		data.setReleaseVersion(editModel.getReleaseVersion());
		data.setInfo(editModel.getInfo());
		data.setThumbnail(editModel.getThumbnail());
		data.setReleaseVersion(editModel.getReleaseVersion());
		data.setProcessType(editModel.getProcessType());

		String subProcessStr = subProcessStr();
		data.setIdeInfo(A2CParser.convert(editModel.getInfo(), 2, subProcessStr, true));
		return data;
	}

	@Override
	public PageSubProcessInfoList getURSubProcessListByParent(String parentId, int pageNum, int pageSize) throws WorkFlowException {
		CheckDataUtil.checkNull(parentId, "parentId");
//		System.out.println("parentId:"+parentId);
//		System.out.println("pageNum:"+pageNum);
//		System.out.println("pageSize:"+pageSize);
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}
		Map<String, Object> params = new HashMap<String, Object>();;
		params.put("process_id", parentId);
		//????????????
		params.put("pageNumber", pageNum);
		params.put("pageSize", pageSize);
		Page<UnreleaseProcessEntity> page = unreleaseProcessService.listStcsmUnreleaseProcess(params);
		PageSubProcessInfoList pageList = new PageSubProcessInfoList();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal((int)page.getTotal());
		List<UnreleaseProcessEntity> unreleaseProcessList = page.getRecords();
		List<SubProcessInfo> list = new ArrayList<SubProcessInfo>();
		for(UnreleaseProcessEntity unreleaseProcess : unreleaseProcessList)
		{
			SubProcessInfo subProcessInfo = new SubProcessInfo();
			String subId = unreleaseProcess.getSubId();
			if(subId == null)
			{
				//??????????????????
				ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
						.processDefinitionKey(unreleaseProcess.getSubKey())
						.latestVersion()
						.singleResult();
				subProcessInfo.setId(processDefinition.getId());
				subProcessInfo.setKey(processDefinition.getKey());
				subProcessInfo.setName(processDefinition.getName());
				subProcessInfo.setReleased(true);
				subProcessInfo.setReleaseVersion(processDefinition.getVersion());

			}
			else
			{
				//??????????????????
				UnReleaseSubProcessData subData = getURSubProcess(subId);
				subProcessInfo.setId(subData.getId());
				subProcessInfo.setKey(subData.getKey());
				subProcessInfo.setName(subData.getName());
				subProcessInfo.setReleased(false);
				subProcessInfo.setReleaseVersion(subData.getReleaseVersion());
			}
			list.add(subProcessInfo);
		}
		pageList.setRows(list);
		return pageList;
	}

	@Override
	public PageUnReleaseSubProcessList getURSubProcessList(String key, String name, int showVersion, int pageNum,
														   int pageSize, int orderByCreateTime, int orderByUpdateTime) {
		Map<String, Object> params = new HashMap<String, Object>();;

		//???????????????
		params.put("process_type", EProcessType.Sub.value);

		//??????key
		if(CheckDataUtil.isNotNull(key))
		{
			params.put("process_key", key);
		}
		//????????????
		if(CheckDataUtil.isNotNull(name))
		{
			params.put("process_name", name);
		}
		EShowVersionType showVersionType  = EShowVersionType.All;
		if(showVersion > 0 && showVersion < EShowVersionType.values().length)
		{
			showVersionType = EShowVersionType.values()[showVersion];
		}
		//????????????????????????
		switch(showVersionType)
		{
			case All:
				break;
			case Has:
				params.put("versionJudge", "1");
				break;
			case NotHas:
				params.put("versionJudge", "0");
				break;
			default:
				break;
		}
		//??????
		String order = makeOrderStr("create_time" , orderByCreateTime);
		if(order == null)
		{
			order = makeOrderStr("update_time" , orderByUpdateTime);
		}
		if(order != null)
		{
			params.put("orderInfo", order);
		}
		//????????????
		params.put("pageNumber", pageNum);
		params.put("pageSize", pageSize);
		Page<EditModelEntity> page = editModelService.listStcsmEditModel(params);
		PageUnReleaseSubProcessList pageList = new PageUnReleaseSubProcessList();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal((int)page.getTotal());
		List<EditModelEntity> editModels = page.getRecords();
		List<UnReleaseSubProcessData> list = new ArrayList<UnReleaseSubProcessData>();
		for(EditModelEntity editModel : editModels)
		{
			UnReleaseSubProcessData data = new UnReleaseSubProcessData();
			data.setId(editModel.getProcessId());
			data.setKey(editModel.getProcessKey());
			data.setName(editModel.getProcessName());
			data.setDescription(editModel.getDescription());
			data.setCreateTime(editModel.getCreateTime());
			data.setUpdateTime(editModel.getUpdateTime());
			data.setReleaseVersion(editModel.getReleaseVersion());
			list.add(data);
		}
		pageList.setRows(list);
		return pageList;
	}

	@Override
	public UnReleaseSubProcessData getURSubProcess(String id) throws WorkFlowException {
		CheckDataUtil.checkNull(id, "id");
		EditModelEntity editModel = editModelService.getStcsmEditModelById(id);
		UnReleaseSubProcessData data = new UnReleaseSubProcessData();
		data.setId(editModel.getProcessId());
		data.setKey(editModel.getProcessKey());
		data.setName(editModel.getProcessName());
		data.setDescription(editModel.getDescription());
		data.setCreateTime(editModel.getCreateTime());
		data.setUpdateTime(editModel.getUpdateTime());
		data.setReleaseVersion(editModel.getReleaseVersion());
		return data;
	}

	@Override
	public PageProcessDefList getProcessDefList(String key, String name, int state, boolean onlyLatestVersion,
												int pageNum, int pageSize) {

		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}
		int firstResult = (pageNum - 1) * pageSize;
		int maxResults = pageSize;
		ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery();
		if (CheckDataUtil.isNotNull(name)) {
			processDefinitionQuery = processDefinitionQuery.processDefinitionNameLike("%" + name + "%");
		}
		String queryKey = ActivitiConstant.MAIN_PROCESS_KEY_HEAD;
		if (CheckDataUtil.isNotNull(key)) {
			queryKey = key;
		}
		processDefinitionQuery = processDefinitionQuery.processDefinitionKeyLike(queryKey + "%");

		if (onlyLatestVersion) {
			processDefinitionQuery = processDefinitionQuery.latestVersion();
		}
		EProcessDefState estate = EProcessDefState.All;
		if(state > 0 || state < EProcessDefState.values().length)
		{
			estate = EProcessDefState.values()[state];
		}
		switch(estate)
		{
			case Active:
				processDefinitionQuery = processDefinitionQuery.active();
				break;
			case Suspend:
				processDefinitionQuery = processDefinitionQuery.suspended();
				break;
			default:
				break;
		}
		processDefinitionQuery = processDefinitionQuery.orderByProcessDefinitionVersion().desc();
		PageProcessDefList pageList = new PageProcessDefList();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal((int)processDefinitionQuery.count());
		if (pageList.getTotal() == 0) {
			return pageList;
		}
		List<ProcessDefinition> processList = processDefinitionQuery.listPage(firstResult, maxResults);
		List<ProcessDefData> list = new ArrayList<ProcessDefData>();
		for (ProcessDefinition process : processList) {
			ProcessDefData data = new ProcessDefData();
			data.setId(process.getId());
			data.setKey(process.getKey());
			data.setName(process.getName());
			data.setState(process.isSuspended() ? EProcessDefState.Suspend.ordinal() : EProcessDefState.Active.ordinal());
			data.setVersion(process.getVersion());
			data.setDeploymentId(process.getDeploymentId());
			data.setDescription(process.getDescription());
			list.add(data);
		}
		pageList.setRows(list);

		return pageList;
	}

	@Override
	public ProcessDefData getProcessDef(String id) throws WorkFlowException {
		CheckDataUtil.checkNull(id, "id");
		ProcessDefinition process = repositoryService.createProcessDefinitionQuery()
				.processDefinitionId(id).singleResult();
		if(process == null)
		{
			return null;
		}
		ProcessDefData data = new ProcessDefData();
		data.setId(process.getId());
		data.setKey(process.getKey());
		data.setName(process.getName());
		data.setState(process.isSuspended() ? EProcessDefState.Suspend.ordinal() : EProcessDefState.Active.ordinal());
		data.setVersion(process.getVersion());
		data.setDeploymentId(process.getDeploymentId());
		data.setDescription(process.getDescription());
		return data;
	}

	@Override
	public PageProcessInstanceList getProcessInstanceListByDef(String processId, int pageNum, int pageSize) throws WorkFlowException {
		CheckDataUtil.checkNull(processId, "processId");
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}
		int firstResult = (pageNum - 1) * pageSize;
		int maxResults = pageSize;
		HistoricProcessInstanceQuery historicProcessInstanceQuery = historyService.createHistoricProcessInstanceQuery().processDefinitionId(processId);
		PageProcessInstanceList pageList = new PageProcessInstanceList();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal((int)historicProcessInstanceQuery.count());
		if (pageList.getTotal() == 0) {
			return pageList;
		}
		List<HistoricProcessInstance> historicProcessInstanceList = historicProcessInstanceQuery.orderByProcessInstanceStartTime().desc().listPage(firstResult, maxResults);
		List<ProcessInstanceData> list = new ArrayList<ProcessInstanceData>();
		for(HistoricProcessInstance historicProcessInstance : historicProcessInstanceList)
		{
			ProcessInstanceData data = makeProcessInstanceData(historicProcessInstance);
			list.add(data);
		}
		pageList.setRows(list);

		return pageList;
	}

	@Override
	public PageSubProcessDefList getSubProcessListByParent(String parentId, int pageNum, int pageSize) throws WorkFlowException {
		CheckDataUtil.checkNull(parentId, "parentId");
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}
		Map<String, Object> params = new HashMap<String, Object>();;
		params.put("process_id", parentId);
		//????????????
		params.put("pageNumber", pageNum);
		params.put("pageSize", pageSize);
		Page<ReleaseProcessEntity> page = releaseProcessService.listStcsmReleaseProcess(params);
		PageSubProcessDefList pageList = new PageSubProcessDefList();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal((int)page.getTotal());
		List<ReleaseProcessEntity> releaseProcessList = page.getRecords();
		List<SubProcessDefData> list = new ArrayList<SubProcessDefData>();
		for(ReleaseProcessEntity releaseProcess : releaseProcessList)
		{
			SubProcessDefData data = new SubProcessDefData();
			//??????????????????
			ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
					.processDefinitionKey(releaseProcess.getSubKey())
					.latestVersion()
					.singleResult();
			data.setId(processDefinition.getId());
			data.setKey(processDefinition.getKey());
			data.setName(processDefinition.getName());
			data.setState(processDefinition.isSuspended() ? EProcessDefState.Suspend.ordinal() : EProcessDefState.Active.ordinal());
			data.setVersion(processDefinition.getVersion());
			data.setDeploymentId(processDefinition.getDeploymentId());
			data.setDescription(processDefinition.getDescription());

			list.add(data);
		}
		pageList.setRows(list);
		return pageList;
	}

	@Override
	public PageSubProcessDefList getSubProcessList(String key, String name, int state, boolean onlyLatestVersion,
												   int pageNum, int pageSize) {

		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}
		int firstResult = (pageNum - 1) * pageSize;
		int maxResults = pageSize;
		ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery();
		if (CheckDataUtil.isNotNull(name)) {
			processDefinitionQuery = processDefinitionQuery.processDefinitionNameLike("%" + name + "%");
		}
		String queryKey = ActivitiConstant.SUB_PROCESS_KEY_HEAD;
		if (CheckDataUtil.isNotNull(key)) {
			queryKey = key;
		}
		processDefinitionQuery = processDefinitionQuery.processDefinitionKeyLike(queryKey + "%");

		if (onlyLatestVersion) {
			processDefinitionQuery = processDefinitionQuery.latestVersion();
		}
		EProcessDefState estate = EProcessDefState.All;
		if(state > 0 || state < EProcessDefState.values().length)
		{
			estate = EProcessDefState.values()[state];
		}
		switch(estate)
		{
			case Active:
				processDefinitionQuery = processDefinitionQuery.active();
				break;
			case Suspend:
				processDefinitionQuery = processDefinitionQuery.suspended();
				break;
			default:
				break;
		}
		processDefinitionQuery = processDefinitionQuery.orderByProcessDefinitionVersion().desc();
		PageSubProcessDefList pageList = new PageSubProcessDefList();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal((int)processDefinitionQuery.count());
		if (pageList.getTotal() == 0) {
			return pageList;
		}
		List<ProcessDefinition> processList = processDefinitionQuery.listPage(firstResult, maxResults);
		List<SubProcessDefData> list = new ArrayList<SubProcessDefData>();
		for (ProcessDefinition process : processList) {
			SubProcessDefData data = new SubProcessDefData();
			data.setId(process.getId());
			data.setKey(process.getKey());
			data.setName(process.getName());
			data.setState(process.isSuspended() ? EProcessDefState.Suspend.ordinal() : EProcessDefState.Active.ordinal());
			data.setVersion(process.getVersion());
			data.setDeploymentId(process.getDeploymentId());
			data.setDescription(process.getDescription());
			list.add(data);
		}
		pageList.setRows(list);

		return pageList;
	}

	@Override
	public SubProcessDefData getSubProcessDef(String id) throws WorkFlowException {
		CheckDataUtil.checkNull(id, "id");
		ProcessDefinition process = repositoryService.createProcessDefinitionQuery()
				.processDefinitionId(id).singleResult();
		if(process == null)
		{
			return null;
		}
		SubProcessDefData data = new SubProcessDefData();
		data.setId(process.getId());
		data.setKey(process.getKey());
		data.setName(process.getName());
		data.setState(process.isSuspended() ? EProcessDefState.Suspend.ordinal() : EProcessDefState.Active.ordinal());
		data.setVersion(process.getVersion());
		data.setDeploymentId(process.getDeploymentId());
		data.setDescription(process.getDescription());
		return data;
	}

	@Override
	public PageSubProcessInstanceList getSubProcessInstanceListByDef(String processId, int pageNum, int pageSize) throws WorkFlowException {
		CheckDataUtil.checkNull(processId, "processId");
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}
		int firstResult = (pageNum - 1) * pageSize;
		int maxResults = pageSize;
		HistoricProcessInstanceQuery historicProcessInstanceQuery = historyService.createHistoricProcessInstanceQuery().processDefinitionId(processId);
		List<HistoricProcessInstance> historicProcessInstanceList = historicProcessInstanceQuery.listPage(firstResult, maxResults);
		PageSubProcessInstanceList pageList = new PageSubProcessInstanceList();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal((int)historicProcessInstanceQuery.count());
		if (pageList.getTotal() == 0) {
			return pageList;
		}
		List<SubProcessInstanceData> list = new ArrayList<SubProcessInstanceData>();
		for(HistoricProcessInstance historicProcessInstance : historicProcessInstanceList)
		{
			SubProcessInstanceData data = new SubProcessInstanceData();
			data.setId(historicProcessInstance.getId());
			data.setName(historicProcessInstance.getName());
			data.setProcessDefId(historicProcessInstance.getProcessDefinitionId());
			data.setStartTime(historicProcessInstance.getStartTime());
			data.setEndTime(historicProcessInstance.getEndTime());
			data.setDeleteReason(historicProcessInstance.getDeleteReason());
			data.setParentProcessInstanceId(historicProcessInstance.getSuperProcessInstanceId());
			if(data.getEndTime() == null)
			{
				//????????????????????????????????????
				ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(data.getId()).singleResult();
				data.setState(processInstance.isSuspended() ? EProcessInstanceState.Suspend.ordinal() : EProcessInstanceState.Active.ordinal());
			}
			else
			{
				data.setState(EProcessInstanceState.Finish.ordinal());
			}
			list.add(data);
		}
		pageList.setRows(list);

		return pageList;
	}

	@Override
	public PageProcessInstanceList getProcessInstanceList(String key, String name, int state, int pageNum, int pageSize,
														  int orderByStartTime, int orderByEndTime) {

		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}

		EProcessInstanceState estate = EProcessInstanceState.valueOf(state);
		if(estate == EProcessInstanceState.Suspend)
		{
			//????????????????????????????????????????????????????????????????????????
			PageProcessInstanceList page = getSAProcessInstanceList(key, name, false, pageNum, pageSize, orderByStartTime);
			return page;
		}
		else if(estate == EProcessInstanceState.Active)
		{
			//????????????????????????????????????????????????????????????????????????
			PageProcessInstanceList page = getSAProcessInstanceList(key, name, true, pageNum, pageSize, orderByStartTime);
			return page;
		}

		int firstResult = (pageNum - 1) * pageSize;
		int maxResults = pageSize;

		PageProcessInstanceList pageList = new PageProcessInstanceList();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal(0);

		HistoricProcessInstanceQuery historicProcessInstanceQuery = historyService.createHistoricProcessInstanceQuery();
		if (CheckDataUtil.isNotNull(name)) {
			historicProcessInstanceQuery = historicProcessInstanceQuery.processDefinitionName(name);
		}
		if (CheckDataUtil.isNotNull(key)) {
			historicProcessInstanceQuery = historicProcessInstanceQuery.processDefinitionKey(key);
		}
		else
		{
			//????????????key???????????????????????????????????????key??????
			ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery();
			processDefinitionQuery = processDefinitionQuery.processDefinitionKeyLike(ActivitiConstant.MAIN_PROCESS_KEY_HEAD + "%");
			processDefinitionQuery = processDefinitionQuery.latestVersion();
			List<ProcessDefinition> processDefList = processDefinitionQuery.list();
			if(processDefList.size() == 0)
			{
				return pageList;
			}
			List<String> keys = new ArrayList<String>();
			for(ProcessDefinition processDef : processDefList)
			{
				keys.add(processDef.getKey());
			}
			historicProcessInstanceQuery = historicProcessInstanceQuery.processDefinitionKeyIn(keys);
		}

		switch(estate)
		{
			case Finish:	//?????????
				historicProcessInstanceQuery = historicProcessInstanceQuery.finished().notDeleted();
				break;
			case Delete:	//????????????????????????
				historicProcessInstanceQuery = historicProcessInstanceQuery.deleted();
				break;
			default:
				break;
		}

		//??????
		EOrderType orderType = getOrderType(orderByStartTime);
		switch(orderType)
		{
			case Asc:
				historicProcessInstanceQuery = historicProcessInstanceQuery.orderByProcessInstanceStartTime().asc();
				break;
			case Desc:
				historicProcessInstanceQuery = historicProcessInstanceQuery.orderByProcessInstanceStartTime().desc();
				break;
			default:
				break;
		}
		if(orderType == EOrderType.None)
		{
			orderType = getOrderType(orderByEndTime);
			switch(orderType)
			{
				case Asc:
					historicProcessInstanceQuery = historicProcessInstanceQuery.orderByProcessInstanceEndTime().asc();
					break;
				case Desc:
					historicProcessInstanceQuery = historicProcessInstanceQuery.orderByProcessInstanceEndTime().desc();
					break;
				default:
					break;
			}
		}

		pageList.setTotal((int)historicProcessInstanceQuery.count());
		if (pageList.getTotal() == 0) {
			return pageList;
		}
		List<HistoricProcessInstance> historicProcessInstanceList = historicProcessInstanceQuery.listPage(firstResult, maxResults);
		List<ProcessInstanceData> list = new ArrayList<ProcessInstanceData>();
		for (HistoricProcessInstance historicProcessInstance : historicProcessInstanceList) {
			ProcessInstanceData data = makeProcessInstanceData(historicProcessInstance);
			list.add(data);
		}
		pageList.setRows(list);
		return pageList;
	}

	@Override
	public ProcessInstanceData getProcessInstance(String id) throws WorkFlowException {
		CheckDataUtil.checkNull(id, "id");
		HistoricProcessInstanceQuery historicProcessInstanceQuery = historyService.createHistoricProcessInstanceQuery();
		historicProcessInstanceQuery = historicProcessInstanceQuery.processInstanceId(id);
		HistoricProcessInstance historicProcessInstance = historicProcessInstanceQuery.singleResult();
		ProcessInstanceData data = makeProcessInstanceData(historicProcessInstance);
		return data;
	}

	@Override
	public PageProcessTaskList getProcessTaskListByInstance(String processInstanceId, int pageNum, int pageSize) throws WorkFlowException {
		CheckDataUtil.checkNull(processInstanceId, "processInstanceId");
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}
		int firstResult = (pageNum - 1) * pageSize;
		int maxResults = pageSize;
		HistoricTaskInstanceQuery historicTaskInstanceQuery = historyService.createHistoricTaskInstanceQuery();
		historicTaskInstanceQuery.processInstanceId(processInstanceId);
		PageProcessTaskList pageList = new PageProcessTaskList();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal((int)historicTaskInstanceQuery.count());
		if (pageList.getTotal() == 0) {
			return pageList;
		}
		List<HistoricTaskInstance> historicTaskInstanceList = historicTaskInstanceQuery.orderByTaskCreateTime().desc().listPage(firstResult, maxResults);
		List<ProcessTaskData> list = new ArrayList<ProcessTaskData>();
		for (HistoricTaskInstance historicTaskInstance : historicTaskInstanceList) {
			ProcessTaskData data = makeProcessTaskData(historicTaskInstance, false);
			list.add(data);
		}
		pageList.setRows(list);
		return pageList;
	}


	@Override
	public PageSubProcessInstanceList getSubProcessInstanceList(String key, String name, int state, int pageNum,
																int pageSize, int orderByStartTime, int orderByEndTime) {
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}

		EProcessInstanceState estate = EProcessInstanceState.valueOf(state);
		if(estate == EProcessInstanceState.Suspend)
		{
			//????????????????????????????????????????????????????????????????????????
			PageSubProcessInstanceList page = getSuspendSubProcessInstanceList(key, name, pageNum, pageSize, orderByStartTime);
			return page;
		}

		int firstResult = (pageNum - 1) * pageSize;
		int maxResults = pageSize;

		PageSubProcessInstanceList pageList = new PageSubProcessInstanceList();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal(0);

		HistoricProcessInstanceQuery historicProcessInstanceQuery = historyService.createHistoricProcessInstanceQuery();
		if (CheckDataUtil.isNotNull(name)) {
			historicProcessInstanceQuery = historicProcessInstanceQuery.processDefinitionName(name);
		}
		if (CheckDataUtil.isNotNull(key)) {
			historicProcessInstanceQuery = historicProcessInstanceQuery.processDefinitionKey(key);
		}
		else
		{
			//????????????key???????????????????????????????????????key??????
			ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery();
			processDefinitionQuery = processDefinitionQuery.processDefinitionKeyLike(ActivitiConstant.SUB_PROCESS_KEY_HEAD + "%");
			processDefinitionQuery = processDefinitionQuery.latestVersion();
			List<ProcessDefinition> processDefList = processDefinitionQuery.list();
			if(processDefList.size() == 0)
			{
				return pageList;
			}
			List<String> keys = new ArrayList<String>();
			for(ProcessDefinition processDef : processDefList)
			{
				keys.add(processDef.getKey());
			}
			historicProcessInstanceQuery = historicProcessInstanceQuery.processDefinitionKeyIn(keys);
		}

		switch(estate)
		{
			case Active:	//?????????
				historicProcessInstanceQuery = historicProcessInstanceQuery.unfinished();
				break;
			case Finish:	//?????????
				historicProcessInstanceQuery = historicProcessInstanceQuery.finished();
				break;
			case Delete:	//????????????????????????
				historicProcessInstanceQuery = historicProcessInstanceQuery.deleted();
				break;
			default:
				break;
		}

		//??????
		EOrderType orderType = getOrderType(orderByStartTime);
		switch(orderType)
		{
			case Asc:
				historicProcessInstanceQuery = historicProcessInstanceQuery.orderByProcessInstanceStartTime().asc();
				break;
			case Desc:
				historicProcessInstanceQuery = historicProcessInstanceQuery.orderByProcessInstanceStartTime().desc();
				break;
			default:
				break;
		}
		if(orderType == EOrderType.None)
		{
			orderType = getOrderType(orderByEndTime);
			switch(orderType)
			{
				case Asc:
					historicProcessInstanceQuery = historicProcessInstanceQuery.orderByProcessInstanceEndTime().asc();
					break;
				case Desc:
					historicProcessInstanceQuery = historicProcessInstanceQuery.orderByProcessInstanceEndTime().desc();
					break;
				default:
					break;
			}
		}


		pageList.setTotal((int)historicProcessInstanceQuery.count());
		if (pageList.getTotal() == 0) {
			return pageList;
		}
		List<HistoricProcessInstance> historicProcessInstanceList = historicProcessInstanceQuery.listPage(firstResult, maxResults);
		List<SubProcessInstanceData> list = new ArrayList<SubProcessInstanceData>();
		for (HistoricProcessInstance historicProcessInstance : historicProcessInstanceList) {
			SubProcessInstanceData data = makeSubProcessInstanceData(historicProcessInstance);
			list.add(data);
		}
		pageList.setRows(list);
		return pageList;
	}

	@Override
	public SubProcessInstanceData getSubProcessInstance(String id) throws WorkFlowException {
		CheckDataUtil.checkNull(id, "id");
		HistoricProcessInstanceQuery historicProcessInstanceQuery = historyService.createHistoricProcessInstanceQuery();
		historicProcessInstanceQuery = historicProcessInstanceQuery.processInstanceId(id);
		HistoricProcessInstance historicProcessInstance = historicProcessInstanceQuery.singleResult();
		SubProcessInstanceData data = makeSubProcessInstanceData(historicProcessInstance);
		return data;
	}

	@Override
	public PageProcessTaskList getProcessTaskList(String key, String processDefId, String processInstanceId,
												  String name, String assignee, int state, int pageNum, int pageSize, int orderByStartTime,
												  int orderByEndTime) {
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}

		int firstResult = (pageNum - 1) * pageSize;
		int maxResults = pageSize;

		HistoricTaskInstanceQuery historicTaskInstanceQuery = historyService.createHistoricTaskInstanceQuery();
		if (CheckDataUtil.isNotNull(key)) {
			historicTaskInstanceQuery = historicTaskInstanceQuery.processDefinitionKey(key);
		}
		if (CheckDataUtil.isNotNull(processDefId)) {
			historicTaskInstanceQuery = historicTaskInstanceQuery.processDefinitionId(processDefId);
		}
		if (CheckDataUtil.isNotNull(processInstanceId)) {
			historicTaskInstanceQuery = historicTaskInstanceQuery.processInstanceId(processInstanceId);
		}
		if (CheckDataUtil.isNotNull(name)) {
			historicTaskInstanceQuery = historicTaskInstanceQuery.taskNameLike("%" + name + "%");
		}
		if (CheckDataUtil.isNotNull(assignee)) {
			historicTaskInstanceQuery = historicTaskInstanceQuery.taskAssignee(assignee);
		}
		if (CheckDataUtil.isNotNull(processInstanceId)) {
			historicTaskInstanceQuery = historicTaskInstanceQuery.processInstanceId(processInstanceId);
		}

		EProcessTaskState estate = EProcessTaskState.valueOf(state);

		switch(estate)
		{
			case Suspend:	//?????????
//				historicTaskInstanceQuery = historicTaskInstanceQuery.;
				break;
			case Active:	//?????????
				historicTaskInstanceQuery = historicTaskInstanceQuery.unfinished();
				break;
			case Finish:	//?????????
				historicTaskInstanceQuery = historicTaskInstanceQuery.finished();
				break;
			case Delete:	//????????????????????????
//				historicTaskInstanceQuery = historicTaskInstanceQuery.taskDeleteReasonLike("");
				break;
			default:
				break;
		}

		//??????
		EOrderType orderType = getOrderType(orderByStartTime);
		switch(orderType)
		{
			case Asc:
				historicTaskInstanceQuery = historicTaskInstanceQuery.orderByHistoricTaskInstanceStartTime().asc();
				break;
			case Desc:
				historicTaskInstanceQuery = historicTaskInstanceQuery.orderByHistoricTaskInstanceStartTime().desc();
				break;
			default:
				break;
		}
		if(orderType == EOrderType.None)
		{
			orderType = getOrderType(orderByEndTime);
			switch(orderType)
			{
				case Asc:
					historicTaskInstanceQuery = historicTaskInstanceQuery.orderByHistoricTaskInstanceEndTime().asc();
					break;
				case Desc:
					historicTaskInstanceQuery = historicTaskInstanceQuery.orderByHistoricTaskInstanceEndTime().desc();
					break;
				default:
					break;
			}
		}

		PageProcessTaskList pageList = new PageProcessTaskList();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal((int)historicTaskInstanceQuery.count());
		if (pageList.getTotal() == 0) {
			return pageList;
		}
		List<HistoricTaskInstance> HistoricTaskInstanceList = historicTaskInstanceQuery.listPage(firstResult, maxResults);
		List<ProcessTaskData> list = new ArrayList<ProcessTaskData>();
		for (HistoricTaskInstance historicTaskInstance : HistoricTaskInstanceList) {
			ProcessTaskData data = makeProcessTaskData(historicTaskInstance, false);
			list.add(data);
		}
		pageList.setRows(list);
		return pageList;
	}

	@Override
	public ProcessTaskData getProcessTask(String id) throws WorkFlowException {
		CheckDataUtil.checkNull(id, "id");
		HistoricTaskInstanceQuery historicTaskInstanceQuery = historyService.createHistoricTaskInstanceQuery();
		historicTaskInstanceQuery = historicTaskInstanceQuery.taskId(id);
		HistoricTaskInstance historicTaskInstance = historicTaskInstanceQuery.singleResult();
		ProcessTaskData data = makeProcessTaskData(historicTaskInstance, true);
		return data;
	}

	@Override
	public PageUserFinishedTaskList getUserFinishedTaskList(String userName, int pageNum, int pageSize, int orderByCount) throws WorkFlowException {
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}
		//?????????redis??????????????????????????????????????????????????????
		if(jedisMgrWf.hasKey(getLoadingKey()))
		{
			throw new WorkFlowException("????????????????????????????????????");
		}
		/*if(mongoTemplate.findAll(MongoEntity.class, getLoadingKey())==null && mongoTemplate.findAll(MongoEntity.class, getLoadingKey()).size()==0){
			//????????????????????????????????????
			throw new WorkFlowException("????????????????????????????????????");
		}*/
		PageUserFinishedTaskList pageList = new PageUserFinishedTaskList();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal(0);
		String key = getUserFinishedKey();
		//??????redis???????????????
		if(!jedisMgrWf.hasKey(key))
		{
			//????????????????????????????????????
			return pageList;
		}
		//???????????????????????????
		if(CheckDataUtil.isNotNull(userName))
		{
//			return pageList;

			// ?????????????????????????????????
			List<UserDepartDto> userdeparlist=authInfoUtil.getUserByName(userName);
			if(userdeparlist==null || userdeparlist.size()<1){
				return pageList;
			}
			int index = -1;
			int firstResult = (pageNum - 1) * pageSize;
			int maxResults = firstResult + pageSize;
			List<UserFinishedTaskData> rows = new ArrayList<UserFinishedTaskData>();
			for(UserDepartDto user : userdeparlist)
			{
				// ????????????id?????????
				int score = jedisMgrWf.getSortSetScore(key, user.getId()).intValue();
				if(score > 0)
				{
					if(index >= firstResult && index < maxResults)
					{
						//????????????????????????
						UserFinishedTaskData userFinishedTaskData = new UserFinishedTaskData();
						userFinishedTaskData.setUserId(user.getId());
						userFinishedTaskData.setUserName(user.getName());
						if(user.getDepartId() != null && user.getDepartName() != null)
						{
							userFinishedTaskData.setUnitId(user.getDepartId());
							userFinishedTaskData.setUnitName(user.getDepartName());
						}
						userFinishedTaskData.setFinishedCount(score);
						rows.add(userFinishedTaskData);
					}
					++index;
				}
			}
			pageList.setTotal(index);
			pageList.setRows(rows);
		}
		else
		{
			//???redis???????????????
			int total = jedisMgrWf.getSortSetLength(key).intValue();
			/*int total=0;
			if(mongoTemplate.findAll(MongoEntity.class, key)!=null){
				total=mongoTemplate.findAll(MongoEntity.class, key).size();
			}*/

			if(total == 0)
			{
				return pageList;
			}
			pageList.setTotal(total);
			int start = (pageNum - 1) * pageSize;
			int end = start + pageSize - 1;
			//???redis??????id??????
			List<UserFinishedTaskData> rows = new ArrayList<UserFinishedTaskData>();
			EOrderType orderType = getOrderType(orderByCount);
			Set<TypedTuple<String>> list;
			if(orderType == EOrderType.Asc)
			{
				list = jedisMgrWf.getSortSetListWithScores(key, start, end);
			}
			else
			{
				list = jedisMgrWf.getSortSetRevListWithScores(key, start, end);
			}
			for(TypedTuple<String> tuple : list)
			{
				TaskUserInfo user = loadUserInfo(tuple.getValue(), true);
				UserFinishedTaskData userFinishedTaskData = new UserFinishedTaskData();
				userFinishedTaskData.setUserId(tuple.getValue());
				userFinishedTaskData.setUserName(user.getUserName());
				userFinishedTaskData.setUnitId(user.getUnitId());
				userFinishedTaskData.setUnitName(user.getUnitName());
				userFinishedTaskData.setFinishedCount(tuple.getScore().intValue());
				rows.add(userFinishedTaskData);
			}
			pageList.setRows(rows);
		}
		return pageList;
	}

	@Override
	public UserFinishedTaskData getUserFinishedTask(String userId) throws WorkFlowException {
		CheckDataUtil.checkNull(userId, "userId");
		//?????????redis??????????????????????????????????????????????????????
		if(jedisMgrWf.hasKey(getLoadingKey()))
		{
			throw new WorkFlowException("????????????????????????????????????");
		}
		/*if(mongoTemplate.findAll(MongoEntity.class, getLoadingKey())==null && mongoTemplate.findAll(MongoEntity.class, getLoadingKey()).size()==0){
			//????????????????????????????????????
			throw new WorkFlowException("????????????????????????????????????");
		}*/
		String key = getUserFinishedKey();
		TaskUserInfo user = loadUserInfo(userId, true);
		UserFinishedTaskData userFinishedTaskData = new UserFinishedTaskData();
		userFinishedTaskData.setUserId(userId);
		userFinishedTaskData.setUserName(user.getUserName());
		userFinishedTaskData.setUnitId(user.getUnitId());
		userFinishedTaskData.setUnitName(user.getUnitName());
		//??????redis???????????????
		if(!jedisMgrWf.hasKey(key))
		{
			return userFinishedTaskData;
		}
		/*if(mongoTemplate.findAll(MongoEntity.class, key)==null && mongoTemplate.findAll(MongoEntity.class, key).size()==0){
			//????????????????????????????????????
			return userFinishedTaskData;
		}*/
		//Query query = new Query(Criteria.where("userId").is(userId));
		userFinishedTaskData.setFinishedCount(jedisMgrWf.getSortSetScore(key, userId).intValue());
		//userFinishedTaskData.setFinishedCount(mongoTemplate.find(query, MongoEntity.class,key).size());
		return userFinishedTaskData;
	}

	@Override
	public PageProcessTaskList getFinishedTasksByUser(String userId, String processDefId, String taskName, int pageNum,
													  int pageSize, int orderByFinishedTime) throws WorkFlowException {
		CheckDataUtil.checkNull(userId, "userId");
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("user_id", userId);
		if(CheckDataUtil.isNotNull(processDefId))
		{
			params.put("process_id", processDefId);
		}
		if(CheckDataUtil.isNotNull(taskName))
		{
			params.put("task_name", taskName);
		}
		//??????
		String order = makeOrderStr("finish_time" , orderByFinishedTime);
//		System.out.println("order:"+order);
		if(order != null)
		{
			params.put("orderInfo", order);
		}
		//????????????
		params.put("pageNumber", pageNum);
		params.put("pageSize", pageSize);
		Page<UserTaskFinishedEntity> pageEntity = userTaskFinishedService.listStcsmUserTaskFinished(params);
		PageProcessTaskList pageList = new PageProcessTaskList();
		pageList.setPageNum((int)pageEntity.getCurrent());
		pageList.setPageSize((int)pageEntity.getSize());
		pageList.setTotal((int)pageEntity.getTotal());
		List<ProcessTaskData> rows = new ArrayList<ProcessTaskData>();
		for(UserTaskFinishedEntity entity : pageEntity.getRecords())
		{
			ProcessTaskData task = new ProcessTaskData();
			task.setId(entity.getTaskId());
			task.setName(entity.getTaskName());
			task.setProcessDefId(entity.getProcessId());
			task.setProcessInstanceId(entity.getProcessInstanceId());
			task.setEndTime(entity.getFinishTime());
			rows.add(task);
		}
		pageList.setRows(rows);
		return pageList;
	}

	/* (non-Javadoc)
	 * @see zdit.zdboot.workflow.console.service.ConsoleService#getUnitFinishedTaskList(java.lang.String, int, int, int)
	 */
	@Override
	public PageUnitFinishedTaskList getUnitFinishedTaskList(int pageNum, int pageSize, int orderByCount) throws WorkFlowException {
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}
		//?????????redis??????????????????????????????????????????????????????
		if(jedisMgrWf.hasKey(getLoadingKey()))
		{
			throw new WorkFlowException("????????????????????????????????????");
		}
		PageUnitFinishedTaskList pageList = new PageUnitFinishedTaskList();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal(0);
		String key = getUnitFinishedKey();
		//??????redis???????????????
		if(!jedisMgrWf.hasKey(key))
		{
			//????????????????????????????????????
			return pageList;
		}
		//???redis???????????????
		int total = jedisMgrWf.getSortSetLength(key).intValue();
		if(total == 0)
		{
			return pageList;
		}
		pageList.setTotal(total);
		int offset = (pageNum - 1) * pageSize;
		int count = pageSize;
		//???redis??????id??????
		List<UnitFinishedTaskData> rows = new ArrayList<UnitFinishedTaskData>();
		EOrderType orderType = getOrderType(orderByCount);
		Set<TypedTuple<String>> list;
		if(orderType == EOrderType.Asc)
		{
			list = jedisMgrWf.getSortSetListWithScores(key, offset, count);
		}
		else
		{
			list = jedisMgrWf.getSortSetRevListWithScores(key, offset, count);
		}
		for(TypedTuple<String> tuple : list)
		{
			UnitFinishedTaskData unitFinishedTaskData = new UnitFinishedTaskData();
			unitFinishedTaskData.setUnitId(tuple.getValue());
			unitFinishedTaskData.setUnitName(tuple.getValue());
			List<SysDepartTreeModel> infolist = authInfoUtil.getDeparts();
			if(infolist!=null && infolist.size()>0){
				String unitId="";
				String unitName="";
				for (SysDepartTreeModel md : infolist ){
					if(md.getId().equals(tuple.getValue())){
						unitId+=md.getId();
						unitName+=md.getDepartName();
					}
				}
				unitFinishedTaskData.setUnitId(unitId);
				unitFinishedTaskData.setUnitName(unitName);
			}
			unitFinishedTaskData.setFinishedCount(tuple.getScore().intValue());
			rows.add(unitFinishedTaskData);
		}
		pageList.setRows(rows);

		return pageList;
	}

	@Override
	public UnitFinishedTaskData getUnitFinishedTask(String unitId) throws WorkFlowException {
		CheckDataUtil.checkNull(unitId, "unitId");
		//?????????redis??????????????????????????????????????????????????????
		if(jedisMgrWf.hasKey(getLoadingKey()))
		{
			throw new WorkFlowException("????????????????????????????????????");
		}
		UnitFinishedTaskData unitFinishedTaskData = new UnitFinishedTaskData();
		unitFinishedTaskData.setUnitId(unitId);
		unitFinishedTaskData.setUnitName(unitId);
		List<SysDepartTreeModel> infolist = authInfoUtil.getDeparts();
		if(infolist!=null && infolist.size()>0){
			for (SysDepartTreeModel md : infolist ){
				if(md.getId().equals(unitId)){
					unitFinishedTaskData.setUnitName(md.getDepartName());
				}
			}
		}
		String key = getUnitFinishedKey();
		//??????redis???????????????
		if(!jedisMgrWf.hasKey(key))
		{
			return unitFinishedTaskData;
		}
		unitFinishedTaskData.setFinishedCount(jedisMgrWf.getSortSetScore(key, unitId).intValue());
		return unitFinishedTaskData;
	}

	@Override
	public PageProcessTaskList getFinishedTasksByUnit(String unitId, String processDefId, String taskName,
													  String userName, int pageNum, int pageSize, int orderByFinishedTime) throws WorkFlowException {
		CheckDataUtil.checkNull(unitId, "unitId");
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("unit_id", unitId);
		if(CheckDataUtil.isNotNull(processDefId))
		{
			params.put("process_id", processDefId);
		}
		if(CheckDataUtil.isNotNull(taskName))
		{
			params.put("task_name", taskName);
		}
		if(CheckDataUtil.isNotNull(userName))
		{
			params.put("user_name", userName);
		}
		//??????
		String order = makeOrderStr("finish_time" , orderByFinishedTime);
		if(order != null)
		{
			params.put("orderInfo", order);
		}
		//????????????
		params.put("pageNumber", pageNum);
		params.put("pageSize", pageSize);
		Page<UserTaskFinishedEntity> pageEntity = userTaskFinishedService.listStcsmUserTaskFinished(params);
		PageProcessTaskList pageList = new PageProcessTaskList();
		pageList.setPageNum((int)pageEntity.getCurrent());
		pageList.setPageSize((int)pageEntity.getSize());
		pageList.setTotal((int)pageEntity.getTotal());
		List<ProcessTaskData> rows = new ArrayList<ProcessTaskData>();
		for(UserTaskFinishedEntity entity : pageEntity.getRecords())
		{
			ProcessTaskData task = new ProcessTaskData();
			task.setId(entity.getTaskId());
			task.setName(entity.getTaskName());
			task.setProcessDefId(entity.getProcessId());
			task.setProcessInstanceId(entity.getProcessInstanceId());
			task.setEndTime(entity.getFinishTime());
			TaskUserInfo assignee = new TaskUserInfo();
			assignee.setUserId(entity.getUserId());
			assignee.setUserName(entity.getUserName());
			task.setAssignee(assignee);
			rows.add(task);
		}
		pageList.setRows(rows);
		return pageList;
	}

	@Override
	public String getProcessPreview(String id) throws WorkFlowException, IOException {
		CheckDataUtil.checkNull(id, "id");
		InputStream inputStream = null;
		try {
			inputStream = repositoryService.getProcessDiagram(id);
		} catch (ActivitiObjectNotFoundException e) {
			throw new WorkFlowException("60501", "?????????????????????");
		}

		if(inputStream == null)
		{
			throw new WorkFlowException("60501", "????????????????????????");
		}
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int length;
		while ((length = inputStream.read(buffer)) != -1) {
			result.write(buffer, 0, length);
		}

		return result.toString("UTF-8");
	}

	@Override
	public String getUnProcessPreview(String id) throws WorkFlowException {
		CheckDataUtil.checkNull(id, "id");
		// ???????????????????????????
		EditModelEntity editModelEntity = editModelService.getStcsmEditModelById(id);
		if(editModelEntity == null)
		{
			throw new WorkFlowException("60501", "????????????????????????");
		}
		return editModelEntity.getThumbnail();
	}

	@Override
	public ProcessDeploymentData getDeploymentData(String deploymentId) throws WorkFlowException {
		CheckDataUtil.checkNull(deploymentId, "deploymentId");
		Deployment deployment = repositoryService.createDeploymentQuery().deploymentId(deploymentId).singleResult();
		ProcessDeploymentData processDeploymentData = new ProcessDeploymentData();
		processDeploymentData.setId(deployment.getId());
		processDeploymentData.setKey(deployment.getKey());
		processDeploymentData.setName(deployment.getName());
		processDeploymentData.setTime(deployment.getDeploymentTime());
		return processDeploymentData;
	}

	@Override
	public List<SimpleInfo> getUnitList(String name) {
		String searchName = null;
		if(CheckDataUtil.isNotNull(name))
		{
			searchName = name;
		}
		List<SysDepartTreeModel> page = null;
		List<SysDepartTreeModel> infolist = authInfoUtil.getDeparts();
		if(infolist!=null && infolist.size()>0){
			for (SysDepartTreeModel md : infolist ){
				if(md.getDepartName().indexOf(name)>-1){
					page.add(md);
				}
			}
		}
		List<SimpleInfo> infoList = new ArrayList<SimpleInfo>();
		if(page.size() == 0)
		{
			return infoList;
		}
		for(SysDepartTreeModel unit : page){
			SimpleInfo info = new SimpleInfo();
			info.setId(unit.getId());
//			info.setCode(unit.getUnitCode());
			info.setCode(unit.getId());
			info.setLabel(unit.getDepartName());
			infoList.add(info);
		}
		return infoList;
	}

	@Override
	public List<SimpleInfo> getSubUnitList(String parentId) throws WorkFlowException {
		CheckDataUtil.checkNull(parentId, "parentId");
		List<SysDepartTreeModel> newMdList=null;
		List<SysDepartTreeModel> infolist = authInfoUtil.getDeparts();
		if(infolist!=null || infolist.size() == 0 ){
			for (SysDepartTreeModel md : infolist){
				if(md.getId().equals(parentId)){
					if(md.getChildren() != null && md.getChildren().size()>0)
						newMdList=md.getChildren();
				}
			}
		}
		if(newMdList==null){
			return null;
		}
		List<SimpleInfo> infoList = new ArrayList<SimpleInfo>();
		for (SysDepartTreeModel md : newMdList)	{
			SimpleInfo info = new SimpleInfo();
			info.setId(md.getId());
			info.setCode(md.getId());
			info.setLabel(md.getDepartName());
			infoList.add(info);
		}
		return infoList;
	}

	@Override
	public List<SimpleInfo> getUserListByUnit(String unitId, String userName) throws WorkFlowException {
		CheckDataUtil.checkNull(unitId, "unitId");
		String searchName = null;
		if(CheckDataUtil.isNotNull(userName))
		{
			searchName = userName;
		}
		List<UserDepartDto> page = authInfoUtil.getUserByName(searchName);
		List<SimpleInfo> infoList = new ArrayList<SimpleInfo>();
		if(page.size() == 0)
		{
			return infoList;
		}
		for(UserDepartDto user : page){
			SimpleInfo info = new SimpleInfo();
			info.setId(user.getId());
			info.setLabel(user.getName());
			infoList.add(info);
		}
		return infoList;
	}

	@Override
	public Object deploymentProcessDefinition(String id, boolean forceUpdateSub) throws WorkFlowException {
		CheckDataUtil.checkNull(id, "id");
		// ???????????????????????????
		EditModelEntity editModelEntity = editModelService.getStcsmEditModelById(id);
		if(editModelEntity == null)
		{
			throw new WorkFlowException("60501", "????????????????????????");
		}
//		logger.debug(editModelEntity.getInfo());
		DeploymentBuilder builder = repositoryService.createDeployment(); // ????????????????????????
		builder = builder.name(editModelEntity.getProcessName());
		builder = builder.key(editModelEntity.getProcessKey());
		builder.addString(editModelEntity.getProcessKey() + ".bpmn", editModelEntity.getInfo());
		builder.addString(editModelEntity.getProcessKey() + ".png", editModelEntity.getThumbnail());
		Deployment deployment = builder.deploy();
//		System.out.println("deployment:"+deployment.getId());
		// ???????????????????????????
		ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery();
		List<ProcessDefinition> prolist = processDefinitionQuery.list();
		ProcessDefinition processDefinition = processDefinitionQuery.deploymentId(deployment.getId()).singleResult();
		//??????????????? ????????????processDefinition ????????????????????????????????????
		Map<String, Object> values = new HashMap<String,Object>();
		values.put("deploymentId",processDefinition.getDeploymentId());
		values.put("id", processDefinition.getId());
		values.put("name", processDefinition.getName());
		values.put("key", processDefinition.getKey());
		values.put("version", processDefinition.getVersion());
		values.put("description", processDefinition.getDescription());
		values.put("category", processDefinition.getCategory());
		values.put("tenantId", processDefinition.getTenantId());
		values.put("engineVersion", processDefinition.getEngineVersion());
		values.put("diagramResourceName", processDefinition.getDiagramResourceName());
		values.put("resourceName", processDefinition.getResourceName());
		// ?????????????????????
		editModelEntity.setReleaseVersion(processDefinition.getVersion());
		editModelService.updateStcsmEditModel(editModelEntity);

		// ?????????????????????
		PageSubProcessInfoList page = getURSubProcessListByParent(editModelEntity.getProcessId(), 1, Integer.MAX_VALUE);
		if(page.getTotal() > 0)
		{
			// ???????????????
			for(int i = 0; i < page.getTotal(); i++)
			{
				SubProcessInfo subProcessInfo = page.getRows().get(i);
				// ????????????????????????????????????
				if(!subProcessInfo.getReleased())
				{
					// ??????????????????????????????
					// ???????????????
//					Map<String, Object> subProcess =(Map<String, Object>) deploymentSubProcessDefinition(subProcessInfo.getId(), forceUpdateSub);
					ProcessDefinition subProcess =deploymentSubProcessDefinition(subProcessInfo.getId(), forceUpdateSub);
					// ??????????????????
					ReleaseProcessEntity releaseProcessEntity = new ReleaseProcessEntity();
					releaseProcessEntity.setProcessId(processDefinition.getId());
					releaseProcessEntity.setProcessKey(processDefinition.getKey());
					releaseProcessEntity.setProcessVersion(processDefinition.getVersion());
					releaseProcessEntity.setSubKey(subProcess.getKey());
					//	System.out.println("subProcess.get(\"key\").toString():"+subProcess.get("key").toString());
					releaseProcessService.saveStcsmReleaseProcess(releaseProcessEntity);
				}
			}
		}
		return values;
	}

	@Override
	public ProcessDefinition deploymentSubProcessDefinition(String id, boolean useNewKey) throws WorkFlowException {

		CheckDataUtil.checkNull(id, "id");
		// ???????????????????????????
		EditModelEntity editModelEntity = editModelService.getStcsmEditModelById(id);
		if(editModelEntity == null)
		{
			throw new WorkFlowException("60501", "???????????????????????????");
		}
		logger.debug(editModelEntity.getInfo());
		DeploymentBuilder builder = repositoryService.createDeployment(); // ????????????????????????
		builder = builder.name(editModelEntity.getProcessName());
		builder = builder.key(editModelEntity.getProcessKey());
		if(useNewKey)
		{
			// ??????????????????????????????????????????
			// ???????????????key?????????key??????
			int index = 0;
			while(true)
			{
				// ?????????key???????????????
				String newkey = editModelEntity.getProcessKey() + index;
				long count = repositoryService.createProcessDefinitionQuery().processDefinitionKey(newkey).count();
				if(count == 0)
				{
					// ????????????????????????

					break;
				}
				++index;
			}
		}

		builder.addString(editModelEntity.getProcessKey() + ".bpmn", editModelEntity.getInfo());
		builder.addString(editModelEntity.getProcessKey() + ".png", editModelEntity.getThumbnail());
		Deployment deployment = builder.deploy();

		// ???????????????????????????
		ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery();
		ProcessDefinition processDefinition = processDefinitionQuery.deploymentId(deployment.getId()).singleResult();
		//??????????????? ????????????processDefinition ????????????????????????????????????
		Map<String, Object> values = new HashMap<String,Object>();
		values.put("deploymentId",processDefinition.getDeploymentId());
		values.put("id", processDefinition.getId());
		values.put("name", processDefinition.getName());
		values.put("key", processDefinition.getKey());
		values.put("version", processDefinition.getVersion());
		values.put("description", processDefinition.getDescription());
		values.put("category", processDefinition.getCategory());
		values.put("tenantId", processDefinition.getTenantId());
		values.put("engineVersion", processDefinition.getEngineVersion());
		values.put("diagramResourceName", processDefinition.getDiagramResourceName());
		values.put("resourceName", processDefinition.getResourceName());
		// ?????????????????????
		editModelEntity.setReleaseVersion(processDefinition.getVersion());
		editModelService.updateStcsmEditModel(editModelEntity);

		return processDefinition;
	}

	@Override
	public boolean deleteUnProcessDef(String id) {
		// ????????????
		editModelService.removeStcsmEditModel(id);

		// ??????????????????
		unreleaseProcessService.removeStcsmUnreleaseProcess(id);

		return true;
	}

	@Override
	public boolean suspendProcessDef(String id, boolean suspendInstance) {
		if(repositoryService.isProcessDefinitionSuspended(id))
			return false;
		repositoryService.suspendProcessDefinitionById(id, suspendInstance, null);
		if(suspendInstance)
		{
			processTaskService.updateStatus(id, null, null, 1);
		}
		return true;
	}

	@Override
	public boolean activateProcessDef(String id, boolean activateInstance) {
		if(!repositoryService.isProcessDefinitionSuspended(id))
			return false;
		repositoryService.activateProcessDefinitionById(id, activateInstance, null);
		if(activateInstance)
		{
			processTaskService.updateStatus(id, null, null, 0);
		}
		return true;
	}

	@Override
	public boolean suspendProcessInst(String userId, String processInstanceId) throws WorkFlowException {

		return workFlowService.suspendProcessInstance(userId, processInstanceId);
	}

	@Override
	public boolean activateProcessInst(String userId, String processInstanceId) throws WorkFlowException {

		return workFlowService.activateProcessInstance(userId, processInstanceId);
	}

	@Override
	public boolean stopProcessInst(String userId, String processInstanceId, String reason) throws WorkFlowException {
		return workFlowService.stopProcessInstance(userId, processInstanceId, reason);
	}

	@Override
	public boolean setTaskAssignee(String userId, String taskId) throws WorkFlowException {
		return workFlowService.setTaskAssignee(userId, taskId);
	}

	@Override
	public boolean suspendProcessTask(String userId, String taskId) throws WorkFlowException {
		CheckDataUtil.checkNull(taskId, "taskId");
		Task task = findTask(taskId);
		if(task == null)
		{
			throw new WorkFlowException("60501", "?????????????????????");
		}
		try {
			runtimeService.suspendProcessInstanceById(task.getProcessInstanceId());
		} catch (ActivitiObjectNotFoundException e) {
			logger.error(e.getMessage());
			throw new WorkFlowException("60501", "?????????????????????");
		} catch (ActivitiException e) {
			logger.warn(e.getMessage());
			throw new WorkFlowException("60501", "????????????????????????");
		}
		processTaskService.updateStatus(null, null, taskId, 1);
		return true;
	}

	@Override
	public boolean activateProcessTask(String userId, String taskId) throws WorkFlowException {
		CheckDataUtil.checkNull(taskId, "taskId");
		Task task = findTask(taskId);
		if(task == null)
		{
			throw new WorkFlowException("60501", "?????????????????????");
		}
		try {
			runtimeService.activateProcessInstanceById(task.getProcessInstanceId());
		} catch (ActivitiObjectNotFoundException e) {
			logger.error(e.getMessage());
			throw new WorkFlowException("60501", "?????????????????????");
		} catch (ActivitiException e) {
			logger.warn(e.getMessage());
			throw new WorkFlowException("60501", "????????????????????????");
		}
		processTaskService.updateStatus(null, null, taskId, 0);
		return true;
	}

	@Override
	@Transactional
	public Map<String,Object> copyUnProcess(@RequestParam  String id,@RequestParam String newKey,@RequestParam String newName) throws WorkFlowException {
		//	System.out.println("map:"+map.get("id"));
		Map<String,Object> resutmap =  new HashMap<String,Object>();
		CheckDataUtil.checkNull(id, "id");
		CheckDataUtil.checkNull(newKey, "newKey");
		CheckDataUtil.checkNull(newName, "newName");
		// ??????????????????
		EditModelEntity editModel = editModelService.getStcsmEditModelById(id);
		if(editModel == null)
		{
			resutmap.put("status","60501");
			resutmap.put("message","???????????????");
			return resutmap;
		}
		//??????key????????????????????????????????????key?????? -by chenx
		if(editModelService.getStcsmEditModelByKey(newKey)!=null){
			resutmap.put("status","60501");
			resutmap.put("message","??????Key????????????????????????");
			return resutmap;
		}
		// ????????????????????????
		String subProcessStr = subProcessStr();
		String ideXml = A2CParser.convert(editModel.getInfo(), editModel.getProcessType(), subProcessStr, true);
		Definitions definitions = C2AParser.convert(ideXml);
		if (definitions == null) { // ????????????
			resutmap.put("status","603");
			resutmap.put("message","????????????");
			return resutmap;
		}
		BpmnModel bpmnModel = definitions.getBpmnModel();

		if (bpmnModel == null) {
			resutmap.put("status","603");
			resutmap.put("message","????????????");
			return resutmap;
//			throw new GraphQLException(603);
		}
		String processIdNew = UUIDUtil.getNextId();
		// ??????
		BpmnProcess bpmnProcess = definitions.getProcess();
		bpmnProcess.setId(newKey);
		bpmnProcess.setName(newName);
		String bpmnString = C2AParser.toXml(definitions);

		editModel.setProcessId(processIdNew);
		editModel.setProcessKey(newKey);
		editModel.setProcessName(newName);
		editModel.setCreateTime(new Date());
		editModel.setUpdateTime(new Date());
		editModel.setInfo(bpmnString);
		ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
				.processDefinitionKey(editModel.getProcessKey()).latestVersion().singleResult();
		int version = 0;
		if (processDefinition != null) {
			version = processDefinition.getVersion();
		}
		editModel.setReleaseVersion(version);

		// ???????????????
		editModelService.saveStcsmEditModel(editModel);
		resutmap.put("status","1");
		resutmap.put("message","????????????");
		return resutmap;
	}

	/**
	 * @Title: makeProcessTaskData
	 * @Description: ???????????????????????????-??????????????????
	 * @param historicTaskInstance
	 * @param allParams
	 * @return  ????????????
	 * @return ProcessTaskData    ????????????
	 *
	 */
	@SuppressWarnings("unchecked")
	private ProcessTaskData makeProcessTaskData(HistoricTaskInstance historicTaskInstance, boolean allParams) {
		if(historicTaskInstance == null)
		{
			return null;
		}
		ProcessTaskData data = new ProcessTaskData();
		data.setId(historicTaskInstance.getId());
		data.setProcessInstanceId(historicTaskInstance.getProcessInstanceId());
		data.setProcessDefId(historicTaskInstance.getProcessDefinitionId());
		data.setStartTime(historicTaskInstance.getStartTime());
		data.setEndTime(historicTaskInstance.getEndTime());
		data.setName(historicTaskInstance.getName());
		data.setAssignee(loadUserInfo(historicTaskInstance.getAssignee(), false));
		data.setParentTaskId(historicTaskInstance.getParentTaskId());
		data.setDescription(historicTaskInstance.getDescription());
		data.setDeleteReason(historicTaskInstance.getDeleteReason());
		data.setDueDate(historicTaskInstance.getDueDate());
		//??????????????????
		if(data.getDeleteReason() != null)
		{
			//?????????
			data.setState(EProcessTaskState.Delete.value);
		}
		else if(historicTaskInstance.getEndTime() != null)
		{
			data.setState(EProcessTaskState.Finish.value);
		}
		else
		{
			Task task = taskService.createTaskQuery().taskId(historicTaskInstance.getId()).singleResult();
			if(task == null)
			{
				data.setState(EProcessTaskState.Finish.value);
			}
			else if(task.isSuspended())
			{
				data.setState(EProcessTaskState.Suspend.value);
			}
			else
			{
				data.setState(EProcessTaskState.Active.value);
			}
		}

		//??????????????????
		if(allParams)
		{
			List<TaskFormData> taskFormDataList = new ArrayList<TaskFormData>();
			List<TaskVariableData> taskVariableDataList = new ArrayList<TaskVariableData>();
			List<TaskUserInfo> taskSignUserInfoList = new ArrayList<TaskUserInfo>();
			List<TaskUserInfo> taskClaimUserInfoList = new ArrayList<TaskUserInfo>();
			List<TaskExVariableData> taskExVariableDataList = new ArrayList<TaskExVariableData>();
			List<TaskClaimGroupInfo> taskClaimGroupInfoList = new ArrayList<TaskClaimGroupInfo>();
			if(historicTaskInstance.getEndTime() != null)
			{
				// ???????????????
				List<HistoricVariableInstance> values = historyService.createHistoricVariableInstanceQuery().taskId(data.getId()).list();
				if(values != null && !values.isEmpty())
				{
					for(HistoricVariableInstance value : values)
					{
						String id = value.getVariableName();
						String[] propertyTypes = id.split(ActivitiConstant.LOCAL_PROPERTY_KEY_SPAN);
						String propertyType = propertyTypes[0];
						switch(propertyType)
						{
							case ActivitiConstant.BUSINESS_KEY:	// ??????URL
							{
								data.setBusinessValue(value.getValue().toString());
							}
							break;
							case ActivitiConstant.SMARTFORM_FORM_ID_HEAD: //??????
							{
								TaskFormData form = new TaskFormData();
								form.setId(propertyTypes[1]);
								form.setTitle(propertyTypes[2]);
								form.setReadable(true);
								form.setWritable(false);
								taskFormDataList.add(form);
							}
							break;
							case ActivitiConstant.SIGN_USERS_FORM_ID_HEAD:	//????????????
							{
								//String usersKey = propertyTypes[1];	//???????????????????????????key
								String signName = propertyTypes[2];

								//????????????????????????
								Object users = value.getValue();
								//????????????????????????
								if (users != null) {
									List<String> useridlist = (List<String>) users;
									for (String userid : useridlist) {
										TaskUserInfo taskSignUserInfo = loadUserInfo(userid, true);
										taskSignUserInfo.setSignName(signName);
										taskSignUserInfoList.add(taskSignUserInfo);
									}
								}
							}
							break;
							case ActivitiConstant.CLAIM_USERS_FORM_ID_HEAD:	//????????????
							{
								//String usersKey = propertyTypes[1];	//???????????????????????????key
								String claimName = propertyTypes[2];

								//????????????????????????
								Object users = value.getValue();
								//????????????????????????
								if (users != null) {
									List<String> userIdList = (List<String>) users;
									for (String userId : userIdList) {
										TaskUserInfo taskClaimUserInfo = loadUserInfo(userId, true);
										taskClaimUserInfo.setSignName(claimName);
										taskClaimUserInfoList.add(taskClaimUserInfo);
									}
								}
							}
							break;
							case ActivitiConstant.CLAIM_GROUPS_FORM_ID_HEAD:	//?????????
							case ActivitiConstant.CLAIM_GROUPS_VALUE_FORM_ID_HEAD: //?????????????????????
							case ActivitiConstant.CLAIM_GROUPS_DATA_FORM_ID_HEAD: //????????????--?????????
							{
								String claimName = propertyTypes[2];
								//?????????????????????
								Object groups = value.getValue();
								//????????????????????????
								if (groups != null) {
									List<String> groupIdList = (List<String>) groups;
									for (String groupId : groupIdList) {
										TaskClaimGroupInfo taskClaimGroupInfo = new TaskClaimGroupInfo();
										taskClaimGroupInfo.setClaimName(claimName);
										taskClaimGroupInfo.setGroupId(groupId);
										taskClaimGroupInfoList.add(taskClaimGroupInfo);
									}
								}
							}
							break;
							case ActivitiConstant.BUSINESS_PROPERTY_KEY:	//?????????????????????
							{
								TaskExVariableData exValue = new TaskExVariableData();
								exValue.setId(propertyTypes[1]);
								exValue.setValue(value.getValue().toString());
								taskExVariableDataList.add(exValue);
							}
							break;
							default:		//???????????????
							{
								if(propertyTypes.length != 3)
								{
									break;
								}
								TaskVariableData taskVariableData = new TaskVariableData();
								taskVariableData.setId(propertyTypes[1]);
								taskVariableData.setName(propertyTypes[2]);
								taskVariableData.setValue(String.valueOf(value.getValue()));
								taskVariableData.setReadable(true);
								taskVariableData.setWritable(false);
								taskVariableDataList.add(taskVariableData);
							}
							break;
						}
					}
				}
			}
			else
			{
				// ???????????????
				org.activiti.engine.form.TaskFormData fdata = formService.getTaskFormData(data.getId());
				List<FormProperty> fvalues = fdata.getFormProperties();
				for (FormProperty fvalue : fvalues) {
					String id = fvalue.getId();
					String[] propertyTypes = id.split(ActivitiConstant.FORM_PROPERTY_KEY_SPAN);
					String propertyType = propertyTypes[0];
					switch(propertyType)
					{
						case ActivitiConstant.SMARTFORM_FORM_ID_HEAD: //??????
						{
							TaskFormData form = new TaskFormData();
							form.setId(propertyTypes[1]);
							form.setTitle(fvalue.getName());
							form.setReadable(fvalue.isReadable());
							form.setWritable(fvalue.isWritable());
							taskFormDataList.add(form);
						}
						break;
						case ActivitiConstant.SIGN_USERS_FORM_ID_HEAD:	//????????????
						{
							String[] userKeys = fvalue.getValue().split(ActivitiConstant.FORM_PROPERTY_KEY_SPAN);
							String usersKey = null;	//???????????????????????????key
							usersKey = userKeys[0];
							String signName = fvalue.getName();

							//????????????????????????
							Object users = taskService.getVariable(data.getId(), usersKey);
							//????????????????????????
							if (users != null) {
								List<String> useridlist = (List<String>) users;
								for (String userid : useridlist) {
									TaskUserInfo taskSignUserInfo = loadUserInfo(userid, true);
									taskSignUserInfo.setSignName(signName);
									taskSignUserInfoList.add(taskSignUserInfo);
								}
							}
						}
						break;
						case ActivitiConstant.CLAIM_USERS_FORM_ID_HEAD:	//????????????
						{
							String[] userKeys = fvalue.getValue().split(ActivitiConstant.FORM_PROPERTY_KEY_SPAN);
							String usersKey = null;	//???????????????????????????key
							usersKey = userKeys[0];
							String claimName = fvalue.getName();

							//????????????????????????
							Object users = taskService.getVariable(data.getId(), usersKey);
							//????????????????????????
							if (users != null) {
								List<String> useridlist = (List<String>) users;
								for (String userid : useridlist) {
									TaskUserInfo taskClaimUserInfo = loadUserInfo(userid, true);
									taskClaimUserInfo.setSignName(claimName);
									taskClaimUserInfoList.add(taskClaimUserInfo);
								}
							}
						}
						break;
						case ActivitiConstant.CLAIM_GROUPS_FORM_ID_HEAD:	//?????????
						{
							//???????????????????????????
						}
						break;
						case ActivitiConstant.BUSINESS_KEY:	//??????url
						{
							data.setBusinessValue(fvalue.getValue());
						}
						break;
						default:		//???????????????
						{
							TaskVariableData taskVariableData = new TaskVariableData();
							taskVariableData.setId(id);
							taskVariableData.setName(fvalue.getName());
							taskVariableData.setValue(fvalue.getValue());
							taskVariableData.setReadable(fvalue.isReadable());
							taskVariableData.setWritable(fvalue.isWritable());
							taskVariableDataList.add(taskVariableData);
						}
						break;
					}
				}

				// ??????????????????
				List<HistoricVariableInstance> values = historyService.createHistoricVariableInstanceQuery().taskId(data.getId()).list();
				if(values != null && !values.isEmpty())
				{
					for(HistoricVariableInstance value : values)
					{
						String id = value.getVariableName();
						String[] propertyTypes = id.split(ActivitiConstant.LOCAL_PROPERTY_KEY_SPAN);
						String propertyType = propertyTypes[0];
						switch(propertyType)
						{
							case ActivitiConstant.BUSINESS_PROPERTY_KEY:	//?????????????????????
							{
								TaskExVariableData exValue = new TaskExVariableData();
								exValue.setId(propertyTypes[1]);
								exValue.setValue(value.getValue().toString());
								taskExVariableDataList.add(exValue);
							}
							break;
							default:
								break;
						}
					}
				}
			}
			data.setTaskFormDataList(taskFormDataList);
			data.setTaskVariableDataList(taskVariableDataList);
			data.setTaskSignUserInfoList(taskSignUserInfoList);
			data.setTaskClaimUserInfoList(taskClaimUserInfoList);
			data.setTaskExVariableDataList(taskExVariableDataList);
			data.setTaskClaimGroupInfoList(taskClaimGroupInfoList);
		}


		return data;
	}

	/**
	 * @Title: makeProcessInstanceData
	 * @Description: ???????????????????????????-??????????????????
	 * @param historicProcessInstance
	 * @return  ????????????
	 * @return ProcessInstanceData    ????????????
	 *
	 */
	private ProcessInstanceData makeProcessInstanceData(HistoricProcessInstance historicProcessInstance)
	{
		ProcessInstanceData data = new ProcessInstanceData();
		data.setId(historicProcessInstance.getId());
		data.setName(historicProcessInstance.getProcessDefinitionName());
		data.setProcessDefId(historicProcessInstance.getProcessDefinitionId());
		data.setStartTime(historicProcessInstance.getStartTime());
		data.setEndTime(historicProcessInstance.getEndTime());
		data.setDeleteReason(historicProcessInstance.getDeleteReason());
		if(data.getEndTime() == null)
		{
			//????????????????????????????????????
			ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(data.getId()).singleResult();
			data.setState(processInstance.isSuspended() ? EProcessInstanceState.Suspend.value : EProcessInstanceState.Active.value);
		}
		else
		{
			//????????????????????????????????????
			if(historicProcessInstance.getDeleteReason() != null)
			{
				data.setState(EProcessInstanceState.Delete.value);
			}
			else
			{
				data.setState(EProcessInstanceState.Finish.value);
			}

		}
		return data;
	}

	/**
	 * @Title: makeProcessInstanceData
	 * @Description: ???????????????????????????-?????????????????????
	 * @param processInstance
	 * @return  ????????????
	 * @return ProcessInstanceData    ????????????
	 *
	 */
	private ProcessInstanceData makeProcessInstanceData(ProcessInstance processInstance)
	{
		ProcessInstanceData data = new ProcessInstanceData();
		data.setId(processInstance.getId());
		data.setName(processInstance.getProcessDefinitionName());
		data.setProcessDefId(processInstance.getProcessDefinitionId());
		data.setStartTime(processInstance.getStartTime());
		data.setState(processInstance.isSuspended() ? EProcessInstanceState.Suspend.value : EProcessInstanceState.Active.value);
		return data;
	}

	/**
	 * @Title: makeSubProcessInstanceData
	 * @Description: ??????????????????????????????-??????????????????
	 * @param historicProcessInstance
	 * @return  ????????????
	 * @return ProcessInstanceData    ????????????
	 *
	 */
	private SubProcessInstanceData makeSubProcessInstanceData(HistoricProcessInstance historicProcessInstance)
	{
		SubProcessInstanceData data = new SubProcessInstanceData();
		data.setId(historicProcessInstance.getId());
		data.setName(historicProcessInstance.getProcessDefinitionName());
		data.setProcessDefId(historicProcessInstance.getProcessDefinitionId());
		data.setStartTime(historicProcessInstance.getStartTime());
		data.setEndTime(historicProcessInstance.getEndTime());
		data.setDeleteReason(historicProcessInstance.getDeleteReason());
		data.setParentProcessInstanceId(historicProcessInstance.getSuperProcessInstanceId());
		if(data.getEndTime() == null)
		{
			//????????????????????????????????????
			ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(data.getId()).singleResult();
			data.setState(processInstance.isSuspended() ? EProcessInstanceState.Suspend.value : EProcessInstanceState.Active.value);
		}
		else
		{
			//????????????????????????????????????
			if(historicProcessInstance.getDeleteReason() != null)
			{
				data.setState(EProcessInstanceState.Delete.value);
			}
			else
			{
				data.setState(EProcessInstanceState.Finish.value);
			}

		}
		return data;
	}

	/**
	 * @Title: makeSubProcessInstanceData
	 * @Description: ??????????????????????????????-?????????????????????
	 * @param processInstance
	 * @return  ????????????
	 * @return SubProcessInstanceData    ????????????
	 *
	 */
	private SubProcessInstanceData makeSubProcessInstanceData(ProcessInstance processInstance) {
		SubProcessInstanceData data = new SubProcessInstanceData();
		data.setId(processInstance.getId());
		data.setName(processInstance.getProcessDefinitionName());
		data.setProcessDefId(processInstance.getProcessDefinitionId());
		data.setStartTime(processInstance.getStartTime());
		data.setState(processInstance.isSuspended() ? EProcessInstanceState.Suspend.value : EProcessInstanceState.Active.value);
		data.setParentProcessInstanceId(processInstance.getParentId());
		return data;
	}

	/**
	 * @Title: getSAProcessInstanceList
	 * @Description: ?????????????????????????????????????????????
	 * @param key
	 * @param name
	 * @param isActive
	 * @param pageNum
	 * @param orderByStartTime
	 * @param pageSize
	 * @return  ????????????
	 * @return PageProcessInstanceList    ????????????
	 *
	 */
	private PageProcessInstanceList getSAProcessInstanceList(String key, String name, boolean isActive,int pageNum,
															 int pageSize, int orderByStartTime) {

		int firstResult = (pageNum - 1) * pageSize;
		int maxResults = pageSize;

		PageProcessInstanceList pageList = new PageProcessInstanceList();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal(0);

		ProcessInstanceQuery processInstanceQuery = runtimeService.createProcessInstanceQuery();
		if(isActive)
		{
			processInstanceQuery = processInstanceQuery.active();
		}
		else
		{
			processInstanceQuery = processInstanceQuery.suspended();
		}
		if (CheckDataUtil.isNotNull(name)) {
			processInstanceQuery = processInstanceQuery.processDefinitionName(name);
		}
		if (CheckDataUtil.isNotNull(key)) {
			processInstanceQuery = processInstanceQuery.processDefinitionKey(key);
		}
		else
		{
			//????????????key???????????????????????????????????????key??????
			ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery();
			processDefinitionQuery = processDefinitionQuery.processDefinitionKeyLike(ActivitiConstant.MAIN_PROCESS_KEY_HEAD + "%");
			processDefinitionQuery = processDefinitionQuery.latestVersion();
			List<ProcessDefinition> processDefList = processDefinitionQuery.list();
			if(processDefList.size() == 0)
			{
				return pageList;
			}
			Set<String> keys = new HashSet<String>();
			for(ProcessDefinition processDef : processDefList)
			{
				keys.add(processDef.getKey());
			}
			processInstanceQuery = processInstanceQuery.processDefinitionKeys(keys);
		}

		//??????
		EOrderType orderType = getOrderType(orderByStartTime);
		switch(orderType)
		{
			case Desc:
				processInstanceQuery = processInstanceQuery.desc();
				break;
			default:
				break;
		}


		pageList.setTotal((int)processInstanceQuery.count());
		if (pageList.getTotal() == 0) {
			return pageList;
		}
		List<ProcessInstance> processInstanceList = processInstanceQuery.listPage(firstResult, maxResults);
		List<ProcessInstanceData> list = new ArrayList<ProcessInstanceData>();
		for (ProcessInstance processInstance : processInstanceList) {
			ProcessInstanceData data = makeProcessInstanceData(processInstance);
			list.add(data);
		}
		pageList.setRows(list);
		return pageList;
	}

	/**
	 * @Title: getSuspendSubProcessInstanceList
	 * @Description: ????????????????????????????????????
	 * @param key
	 * @param name
	 * @param pageNum
	 * @param pageSize
	 * @param orderByStartTime
	 * @return  ????????????
	 * @return PageSubProcessInstanceList    ????????????
	 *
	 */
	private PageSubProcessInstanceList getSuspendSubProcessInstanceList(String key, String name, int pageNum,
																		int pageSize, int orderByStartTime) {
		int firstResult = (pageNum - 1) * pageSize;
		int maxResults = pageSize;

		PageSubProcessInstanceList pageList = new PageSubProcessInstanceList();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal(0);

		ProcessInstanceQuery processInstanceQuery = runtimeService.createProcessInstanceQuery();
		if (CheckDataUtil.isNotNull(name)) {
			processInstanceQuery = processInstanceQuery.processDefinitionName(name);
		}
		if (CheckDataUtil.isNotNull(key)) {
			processInstanceQuery = processInstanceQuery.processDefinitionKey(key);
		}
		else
		{
			//????????????key???????????????????????????????????????key??????
			ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery();
			processDefinitionQuery = processDefinitionQuery.processDefinitionKeyLike(ActivitiConstant.SUB_PROCESS_KEY_HEAD + "%");
			processDefinitionQuery = processDefinitionQuery.latestVersion();
			List<ProcessDefinition> processDefList = processDefinitionQuery.list();
			if(processDefList.size() == 0)
			{
				return pageList;
			}
			Set<String> keys = new HashSet<String>();
			for(ProcessDefinition processDef : processDefList)
			{
				keys.add(processDef.getKey());
			}
			processInstanceQuery = processInstanceQuery.processDefinitionKeys(keys);
		}


		//??????
		EOrderType orderType = getOrderType(orderByStartTime);
		switch(orderType)
		{
			case Desc:
				processInstanceQuery = processInstanceQuery.desc();
				break;
			default:
				break;
		}


		pageList.setTotal((int)processInstanceQuery.count());
		if (pageList.getTotal() == 0) {
			return pageList;
		}
		List<ProcessInstance> processInstanceList = processInstanceQuery.listPage(firstResult, maxResults);
		List<SubProcessInstanceData> list = new ArrayList<SubProcessInstanceData>();
		for (ProcessInstance processInstance : processInstanceList) {
			SubProcessInstanceData data = makeSubProcessInstanceData(processInstance);
			list.add(data);
		}
		pageList.setRows(list);
		return pageList;
	}

	/**
	 * @Title: loadUserInfo
	 * @Description: ??????????????????
	 * @param userId userid
	 * @return  ????????????
	 * @return UserInfo    ????????????
	 *
	 */
	private TaskUserInfo loadUserInfo(String userId, boolean loadUnit)
	{
		TaskUserInfo info = new TaskUserInfo();
		if(userId == null)
		{
			info.setUserId("");
			info.setUserName("");
			if(loadUnit)
			{
				info.setUnitId("");
				info.setUnitName("");
			}
			return info;
		}
		info.setUserId(userId);
		// ????????????????????????????????????
		try {
			info.setUserName(userId);
			List<UserDepartDto> userdeparlist=authInfoUtil.getUserById(userId);
			if(userdeparlist==null || userdeparlist.size()<1){
				info.setUserName(userId);
			} else {
				info.setUserName(userdeparlist.get(0).getName());
				if (loadUnit) {
					String unitid="";
					String unitname="";
					for(UserDepartDto userdepar : userdeparlist) {
						unitid+=userdepar.getDepartId();
						unitname+=userdepar.getDepartName();
					}
					info.setUnitId(unitid);
					info.setUnitName(unitname);
				}
			}
		} catch (Exception e) {

		}
		return info;
	}

	/**
	 * @Title: findTask
	 * @Description: ????????????
	 * @param taskId
	 * @return  ????????????
	 * @return Task    ????????????
	 *
	 */
	private Task findTask(String taskId)
	{
		Task task = taskService // ???????????????????????????????????????Service
				.createTaskQuery() // ????????????????????????
				.taskId(taskId) // ????????????????????????
				.singleResult();
		return task;
	}

	/**
	 * @Title: getUserFinishedKey
	 * @Description: ?????????????????????????????????key
	 * @return  ????????????
	 * @return String    ????????????
	 *
	 */
	private String getUserFinishedKey()
	{
		return JedisMgr_wf.KeyHead + ActivitiConstant.REDIS_USER_FINISHED_VIEW_KEY;
	}

	/**
	 * @Title: getUnitFinishedKey
	 * @Description: ?????????????????????????????????key
	 * @return  ????????????
	 * @return String    ????????????
	 *
	 */
	private String getUnitFinishedKey()
	{
		return JedisMgr_wf.KeyHead + ActivitiConstant.REDIS_UNIT_FINISHED_VIEW_KEY;
	}

	/**
	 * @Title: getLoadingKey
	 * @Description: ??????????????????????????????key
	 * @return  ????????????
	 * @return String    ????????????
	 *
	 */
	private String getLoadingKey()
	{
		return JedisMgr_wf.KeyHead + ActivitiConstant.REDIS_FINISHED_LOADING_KEY;
	}



}
