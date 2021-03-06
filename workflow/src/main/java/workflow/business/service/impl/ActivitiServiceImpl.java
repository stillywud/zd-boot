package workflow.business.service.impl;

import auth.domain.common.dto.UserDepartDto;
import auth.domain.common.service.AuthInfo;
import com.baomidou.dynamic.datasource.annotation.DS;
import workflow.business.service.ActivitiService;
import workflow.business.service.UserTaskFinishedService;
import com.alibaba.fastjson.JSONObject;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.FlowNode;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.engine.*;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.form.TaskFormData;
import org.activiti.engine.history.*;
import org.activiti.engine.impl.cmd.NeedsActiveTaskCmd;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntityManagerImpl;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import workflow.business.mapper.UserReplaceDao;
import workflow.business.model.UserReplace;
import workflow.business.model.UserReplaceInfo;
import workflow.business.model.*;
import workflow.business.service.WorkflowService;
import workflow.common.constant.ActivitiConstant;
import workflow.common.constant.ActivitiConstant.EOrderType;
import workflow.common.error.WorkFlowException;
import workflow.common.redis.JedisMgr_wf;
import workflow.common.utils.CheckDataUtil;
import workflow.business.model.entity.UserTaskFinishedEntity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.Map.Entry;
/**
 * @ClassName: ActivitiServiceImpl
 * @Description: ?????????????????????
 * @author KaminanGTO
 * @date 2018???9???11??? ??????12:57:54
 *
 */
/*@Service(interfaceClass = ActivitiService.class, retries = 0)
@Component*/
@Component
@Service("activitiService")
@DS("master")
public class ActivitiServiceImpl implements ActivitiService {

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
	private ManagementService managementService;

	@Autowired
	private UserTaskFinishedService userTaskFinishedService;

	@Autowired
	private JedisMgr_wf jedisMgrWf;

	@Autowired
	private UserReplaceDao userReplaceDao;

	@Autowired
	private AuthInfo authInfoUtil;

	@Autowired
	private WorkflowService workflowService;

	@Override
	public PageList<ProcessSampleData> getProcessList(String name, boolean onlyLatestVersion, int pageNum, int pageSize)
			throws WorkFlowException {
		return getProcessList(name, onlyLatestVersion, null, pageNum, pageSize);
	}

	@Override
	public PageList<ProcessSampleData> getProcessList(String name, boolean onlyLatestVersion, String businessType,
													  int pageNum, int pageSize) throws WorkFlowException {
		return workflowService.getProcessList(name, onlyLatestVersion, businessType, pageNum, pageSize);
	}

	@Override
	@Transactional
	public TaskData startProcessInstanceById(String userId, String processId, Map<String, Object> values)
			throws WorkFlowException {
		return startProcessInstanceById(userId, processId, values, null);
	}

	@Override
	public TaskData startProcessInstanceById(String userId, String processId, Map<String, Object> values,
											 String businessKey) throws WorkFlowException {
		CheckDataUtil.checkNull(processId, "processId");
		ProcessInstance pi = null;
		// ???????????????
		if (userId != null && !userId.isEmpty()) {
			if (values == null) {
				values = new HashMap<String, Object>();
			}
			values.put(ActivitiConstant.STARTER_KEY, userId);
		}
		// ????????????key?????????????????????????????????key
		if(CheckDataUtil.isNull(businessKey))
		{
			businessKey = ActivitiConstant.DEF_PROCESS_INST_BUSINESS_KEY;
		}
		try {
			if (values == null) {
				pi = runtimeService.startProcessInstanceById(processId, businessKey);
			} else {
				pi = runtimeService.startProcessInstanceById(processId, businessKey, values);
			}

		} catch (ActivitiObjectNotFoundException e) {
			logger.error(e.getMessage());
			throw new WorkFlowException("60501", "???????????????");
		}

		String taskId = initProcessParams(pi);
		if (taskId == null) {
			return null;
		}
		return getTaskInfo(taskId);
	}

	@Override
	@Transactional
	public TaskData startProcessInstanceByKey(String userId, String processKey, Map<String, Object> values)
			throws WorkFlowException {
		return startProcessInstanceByKey(userId, processKey, values, null);
	}

	@Override
	public TaskData startProcessInstanceByKey(String userId, String processKey, Map<String, Object> values,
											  String businessKey) throws WorkFlowException {
		CheckDataUtil.checkNull(processKey, "processKey");
		ProcessInstance pi = null;

		// ???????????????
		if (CheckDataUtil.isNotNull(userId)) {
			if (values == null) {
				values = new HashMap<String, Object>();
			}
			values.put(ActivitiConstant.STARTER_KEY, userId);
		}
		// ????????????key?????????????????????????????????key
		if(CheckDataUtil.isNull(businessKey))
		{
			businessKey = ActivitiConstant.DEF_PROCESS_INST_BUSINESS_KEY;
		}
		try {
			if (values == null) {
				pi = runtimeService.startProcessInstanceByKey(processKey, businessKey);
			} else {
				pi = runtimeService.startProcessInstanceByKey(processKey, businessKey, values);
			}
		} catch (ActivitiObjectNotFoundException e) {
			logger.error(e.getMessage());
			throw new WorkFlowException("60501", "???????????????");
		}

		String taskId = initProcessParams(pi);
		if (taskId == null) {
			return null;
		}
		return getTaskInfo(taskId);
	}

	/**
	 * ?????????????????????
	 *
	 * @param pi
	 */
	private String initProcessParams(ProcessInstance pi) {
		Task task = null;
		try {
			task = taskService.createTaskQuery().processInstanceId(pi.getId()).singleResult();
		} catch (ActivitiException e) {
			return null;
		}
		if (task == null) {
			return null;
		}

		Map<String, Object> values = taskService.getVariables(task.getId());
		Map<String, List<String>> signUsers = new HashMap<String, List<String>>();
		Map<String, List<String>> claimUsers = new HashMap<String, List<String>>();
		Map<String, List<String>> claimJobs = new HashMap<String, List<String>>();
		for (String key : values.keySet()) {
			int idx = key.indexOf(ActivitiConstant.SIGN_USERS_ID_HEAD); // ????????????
			if (idx == 0) {
				Object value = values.get(key);
				if (value != null) {
					String strValue = value.toString();
					if (!strValue.isEmpty()) {
						String[] users = strValue.split(",");
						List<String> userlist = java.util.Arrays.asList(users);
						signUsers.put(key, userlist);
					}
				}
				continue;
			}
			idx = key.indexOf(ActivitiConstant.CLAIM_USERS_ID_HEAD); // ????????????
			if (idx == 0) {
				Object value = values.get(key);
				if (value != null) {
					String strValue = value.toString();
					if (!strValue.isEmpty()) {
						String[] users = strValue.split(",");
						List<String> userlist = java.util.Arrays.asList(users);
						claimUsers.put(key, userlist);
					}
				}
				continue;
			}
			idx = key.indexOf(ActivitiConstant.CLAIM_GROUP_JOBS_HEAD); // ????????????
			if (idx == 0) {
				Object value = values.get(key);
				if (value != null) {
					String strValue = value.toString();
					if (!strValue.isEmpty()) {
						String[] jobs = strValue.split(",");
						List<String> jobList = java.util.Arrays.asList(jobs);
						claimJobs.put(key, jobList);
					}
				}
				continue;
			}
		}
		if (!signUsers.isEmpty()) {
			taskService.setVariables(task.getId(), signUsers);
		}
		if (!claimUsers.isEmpty()) {
			taskService.setVariables(task.getId(), claimUsers);
		}
		if (!claimJobs.isEmpty()) {
			taskService.setVariables(task.getId(), claimJobs);
		}
		return task.getId();
	}

	@Override
	@Transactional
	public boolean stopProcessInstance(String userId, String processInstanceId, String reason)
			throws WorkFlowException {
		return workflowService.stopProcessInstance(userId,processInstanceId,reason);

	}

	@Override
	@Transactional
	public boolean suspendProcessInstance(String userId, String processInstanceId) throws WorkFlowException {
		return workflowService.suspendProcessInstance(userId, processInstanceId);
	}

	@Override
	@Transactional
	public boolean activateProcessInstance(String userId, String processInstanceId) throws WorkFlowException {
		return workflowService.activateProcessInstance(userId, processInstanceId);
	}

	@Override
	public PageList<TaskSampleData> getTasksByUser(String userId, String processId, String taskName, String taskDefId,
												   boolean showClaim, Date startTime, Date endTime, int pageNum, int pageSize) throws WorkFlowException {
		return getTasksByUser(userId, null, null, null, processId, null, taskName, taskDefId, showClaim, startTime,
				endTime, pageNum, pageSize);
	}

	@Override
	public PageList<TaskSampleData> getTasksByUser(String userId, String region, String unitId, List<String> jobIdList,
												   String processId, String taskName, String taskDefId, boolean showClaim, Date startTime, Date endTime,
												   int pageNum, int pageSize) throws WorkFlowException {
		return getTasksByUser(userId, region, unitId, jobIdList, processId, null, taskName, taskDefId, showClaim,
				startTime, endTime, pageNum, pageSize);
	}

	@Override
	public PageList<TaskSampleData> getTasksByUser(String userId, String region, String unitId, List<String> jobIdList,
												   String processId, String processBusinessType, String taskName, String taskDefId, boolean showClaim,
												   Date startTime, Date endTime, int pageNum, int pageSize) throws WorkFlowException {
		List<String> unitIds = null;
		if (CheckDataUtil.isNotNull(unitId)) {
			unitIds = new ArrayList<String>();
			unitIds.add(unitId);
		}
		return getTasksByUser(userId, region, unitIds, null, jobIdList, processId, processBusinessType, taskName,
				taskDefId, showClaim, startTime, endTime, pageNum, pageSize);
	}

	@Override
	public PageList<TaskSampleData> getTasksByUser(String userId, String region, List<String> unitIds,
												   List<String> adminUnitIds, List<String> jobIdList, String processId, String processBusinessType,
												   String taskName, String taskDefId, boolean showClaim, Date startTime, Date endTime, int pageNum,
												   int pageSize) throws WorkFlowException {
		return getTasksByUser(userId, region, unitIds, adminUnitIds, jobIdList, processId, null, processBusinessType, null, taskName, taskDefId, showClaim, startTime, endTime, pageNum, pageSize);
	}

	@Override
	public PageList<TaskSampleData> getTasksByUser(String userId, String region, List<String> unitIds,
												   List<String> adminUnitIds, List<String> jobIdList, String processId, String processKey,
												   String processBusinessType, List<String> processBusinessKeyList, String taskName, String taskDefId, boolean showClaim,
												   Date startTime, Date endTime, int pageNum, int pageSize) throws WorkFlowException {
		return getTasksByUser(userId, region, unitIds, adminUnitIds, jobIdList, processId, processKey, processBusinessType, processBusinessKeyList, taskName, taskDefId, showClaim, startTime, endTime, false, EOrderType.Desc, pageNum, pageSize);
	}

	@Override
	public PageList<TaskSampleData> getTasksByUser(String userId, String region, List<String> unitIds,
												   List<String> adminUnitIds, List<String> jobIdList, String processId, String processKey,
												   String processBusinessType, List<String> processBusinessKeyList, String taskName, String taskDefId,
												   boolean showClaim, Date startTime, Date endTime, boolean hasBusinessKey, EOrderType orderByCreate, int pageNum,
												   int pageSize) throws WorkFlowException {
		// ??????????????????????????????id
		//CheckDataUtil.checkNull(userId, "userId");
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}

		int firstResult = (pageNum - 1) * pageSize;
		int maxResults = pageSize;

		TaskQuery taskQuery = taskService // ???????????????????????????????????????Service
				.createTaskQuery() // ????????????????????????
				.active(); // ?????????????????????????????????

		boolean isQueryJobs = false;
		List<String> claimGroupList = new ArrayList<String>();
		// ?????????????????????????????????
		if (CheckDataUtil.isNotNull(jobIdList)) {
			isQueryJobs = true;
			taskQuery = taskQuery.or();
			claimGroupList.addAll(jobIdList);
			// ???????????????????????????
			if (CheckDataUtil.isNotNull(unitIds)) {
				for (String jobId : jobIdList) {
					for (String unitId : unitIds) {
						String claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
								+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
								+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
						claimGroupList.add(claimGroup);
					}
				}
			}
			// ????????????????????????????????????
			if (CheckDataUtil.isNotNull(adminUnitIds)) {
				for (String jobId : jobIdList) {
					for (String unitId : adminUnitIds) {
						String claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
								+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_ADMIN_UNIT
								+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
						claimGroupList.add(claimGroup);
						// ???????????????????????????
						claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
								+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
								+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
						claimGroupList.add(claimGroup);
					}
				}
			}
			// ???????????????????????????
			if (CheckDataUtil.isNotNull(region)) {
				for (String jobId : jobIdList) {
					String claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
							+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_REGION + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
							+ region;
					claimGroupList.add(claimGroup);
				}
			}

		}
		// ???????????????????????????
		if (CheckDataUtil.isNotNull(unitIds)) {
			if (!isQueryJobs) {
				isQueryJobs = true;
				taskQuery = taskQuery.or();
			}
			for (String unitId : unitIds) {
				String claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
						+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
				claimGroupList.add(claimGroup);
			}
		}
		// ????????????????????????????????????
		if (CheckDataUtil.isNotNull(adminUnitIds)) {
			if (!isQueryJobs) {
				isQueryJobs = true;
				taskQuery = taskQuery.or();
			}
			for (String unitId : adminUnitIds) {
				String claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_ADMIN_UNIT
						+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
				claimGroupList.add(claimGroup);
				// ???????????????????????????
				claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
						+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
				claimGroupList.add(claimGroup);
			}
		}
		// ???????????????????????????
		if (CheckDataUtil.isNotNull(region)) {
			if (!isQueryJobs) {
				isQueryJobs = true;
				taskQuery = taskQuery.or();
			}
			String claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_REGION
					+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + region;
			claimGroupList.add(claimGroup);
		}
		if (!claimGroupList.isEmpty()) {
			taskQuery = taskQuery.taskCandidateGroupIn(claimGroupList);
		}
		if (CheckDataUtil.isNotNull(userId))
		{
			// ???????????????????????????????????????
			List<String> replaceUsers = getReplaceUsers(userId, processId);
			if (replaceUsers != null) {
				replaceUsers.add(userId);
			}
			// ???????????????????????????
			if (showClaim) {
				if (replaceUsers != null) {
					if (isQueryJobs) {
						for (String u : replaceUsers) {
							taskQuery = taskQuery.taskCandidateOrAssigned(u);
						}
					} else {
						taskQuery = taskQuery.or();
						for (String u : replaceUsers) {
							taskQuery = taskQuery.taskCandidateOrAssigned(u);
						}
						taskQuery = taskQuery.endOr();
					}
				} else {
					taskQuery = taskQuery.taskCandidateOrAssigned(userId);
				}

			} else {
				if (replaceUsers != null) {
					taskQuery = taskQuery.taskAssigneeIds(replaceUsers);
				} else {
					taskQuery = taskQuery.taskAssignee(userId); // ??????????????????????????????????????????
				}
			}
		}
		// ????????????????????????????????????or??????
		if (isQueryJobs) {
			taskQuery = taskQuery.endOr();
		}

		if (CheckDataUtil.isNotNull(processId)) {
			taskQuery = taskQuery.processDefinitionId(processId); // ??????????????????ID
		}
		if (CheckDataUtil.isNotNull(processKey)) {
			taskQuery = taskQuery.processDefinitionKey(processKey); // ??????????????????key
		}
		if (CheckDataUtil.isNotNull(processBusinessType)) {
			taskQuery = taskQuery.processDefinitionKeyLike("%\\" + ActivitiConstant.PROCESS_KEY_SPAN + processBusinessType
					+ "\\" + ActivitiConstant.PROCESS_KEY_SPAN + "%"); // ????????????????????????
		}
		if (CheckDataUtil.isNotNull(taskName)) {
			taskQuery = taskQuery.taskNameLike(taskName + "%"); // ????????????????????????
		}
		if (CheckDataUtil.isNotNull(taskDefId)) {
			taskQuery = taskQuery.taskDefinitionKey(taskDefId); // ????????????????????????ID
		}
		if (startTime != null) {
			taskQuery = taskQuery.taskCreatedAfter(startTime);
		}
		if (endTime != null) {
			taskQuery = taskQuery.taskCreatedBefore(endTime);
		}
		taskQuery = taskQuery.orderByTaskCreateTime().asc();
		List<Task> taskList = null;
		if (CheckDataUtil.isNotNull(processBusinessKeyList)) {
			taskList = new ArrayList<Task>();
			// ??????????????????key????????????????????????????????????
			for(String bKey : processBusinessKeyList)
			{
				taskList.addAll(taskQuery.processInstanceBusinessKey(bKey).list());
			}
		}
		else
		{
			// ????????????key?????????????????????????????????????????????
			taskQuery = taskQuery.processInstanceBusinessKey(ActivitiConstant.DEF_PROCESS_INST_BUSINESS_KEY); // ????????????key
		}
		PageList<TaskSampleData> pageList = new PageList<TaskSampleData>();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal(taskList == null ? (int) taskQuery.count() : taskList.size());
		if (pageList.getTotal() == 0) {
			return pageList;
		}
		if(taskList == null)
		{
			// ??????
			switch(orderByCreate)
			{
				case Asc:
					taskQuery = taskQuery.orderByTaskCreateTime().asc();
					break;
				case Desc:
					taskQuery = taskQuery.orderByTaskCreateTime().desc();
					break;
				case None:
					break;
				default:
					break;

			}
			taskList = taskQuery.listPage(firstResult, maxResults);
		}
		else
		{
			// ?????????
			switch(orderByCreate)
			{
				case Asc:
					taskList.sort((t1, t2) -> Long.compare(t1.getCreateTime().getTime() , t2.getCreateTime().getTime()));
					break;
				case Desc:
					taskList.sort((t1, t2) -> Long.compare(t2.getCreateTime().getTime() , t1.getCreateTime().getTime()));
					break;
				case None:
					break;
				default:
					break;

			}
			// ??????????????????
			int start = (pageNum - 1) * pageSize;
			int end = Math.min(taskList.size(), start + pageSize);
			if (start >= taskList.size())
			{
				return pageList;
			}
			taskList = taskList.subList(start, end);
		}

		List<TaskSampleData> rows = new ArrayList<TaskSampleData>();
		// ?????????????????????????????????????????????id??????
		Map<String, TaskSampleData> paramsCache = new HashMap<String, TaskSampleData>();
		for (Task task : taskList) {
			int stateType = 1;
			if (task.getAssignee() == null) {
				stateType = 2;
			}
			TaskSampleData taskSampleData = makeTaskSampleData(task, stateType, hasBusinessKey, paramsCache);

			rows.add(taskSampleData);
		}
		pageList.setRows(rows);
		return pageList;
	}

	@Override
	public List<String> getProcessInstIdsByUser(String userId, String region, List<String> unitIds,
												List<String> adminUnitIds, List<String> jobIdList, String processId, String processKey,
												String processBusinessType, List<String> processBusinessKeyList, String taskName, String taskDefId,
												boolean showClaim, Date startTime, Date endTime) throws WorkFlowException {

		CheckDataUtil.checkNull(userId, "userId");

		TaskQuery taskQuery = taskService // ???????????????????????????????????????Service
				.createTaskQuery() // ????????????????????????
				.active(); // ?????????????????????????????????

		boolean isQueryJobs = false;
		List<String> claimGroupList = new ArrayList<String>();
		// ?????????????????????????????????
		if (CheckDataUtil.isNotNull(jobIdList)) {
			isQueryJobs = true;
			taskQuery = taskQuery.or();
			claimGroupList.addAll(jobIdList);
			// ???????????????????????????
			if (CheckDataUtil.isNotNull(unitIds)) {
				for (String jobId : jobIdList) {
					for (String unitId : unitIds) {
						String claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
								+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
								+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
						claimGroupList.add(claimGroup);
					}
				}
			}
			// ????????????????????????????????????
			if (CheckDataUtil.isNotNull(adminUnitIds)) {
				for (String jobId : jobIdList) {
					for (String unitId : adminUnitIds) {
						String claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
								+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_ADMIN_UNIT
								+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
						claimGroupList.add(claimGroup);
						// ???????????????????????????
						claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
								+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
								+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
						claimGroupList.add(claimGroup);
					}
				}
			}
			// ???????????????????????????
			if (CheckDataUtil.isNotNull(region)) {
				for (String jobId : jobIdList) {
					String claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
							+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_REGION + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
							+ region;
					claimGroupList.add(claimGroup);
				}
			}

		}
		// ???????????????????????????
		if (CheckDataUtil.isNotNull(unitIds)) {
			if (!isQueryJobs) {
				isQueryJobs = true;
				taskQuery = taskQuery.or();
			}
			for (String unitId : unitIds) {
				String claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
						+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
				claimGroupList.add(claimGroup);
			}
		}
		// ????????????????????????????????????
		if (CheckDataUtil.isNotNull(adminUnitIds)) {
			if (!isQueryJobs) {
				isQueryJobs = true;
				taskQuery = taskQuery.or();
			}
			for (String unitId : adminUnitIds) {
				String claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_ADMIN_UNIT
						+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
				claimGroupList.add(claimGroup);
				// ???????????????????????????
				claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
						+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
				claimGroupList.add(claimGroup);
			}
		}
		// ???????????????????????????
		if (CheckDataUtil.isNotNull(region)) {
			if (!isQueryJobs) {
				isQueryJobs = true;
				taskQuery = taskQuery.or();
			}
			String claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_REGION
					+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + region;
			claimGroupList.add(claimGroup);
		}
		if (!claimGroupList.isEmpty()) {
			taskQuery = taskQuery.taskCandidateGroupIn(claimGroupList);
		}
		// ???????????????????????????????????????
		List<String> replaceUsers = getReplaceUsers(userId, processId);
		if (replaceUsers != null) {
			replaceUsers.add(userId);
		}
		// ???????????????????????????
		if (showClaim) {
			if (replaceUsers != null) {
				if (isQueryJobs) {
					for (String u : replaceUsers) {
						taskQuery = taskQuery.taskCandidateOrAssigned(u);
					}
				} else {
					taskQuery = taskQuery.or();
					for (String u : replaceUsers) {
						taskQuery = taskQuery.taskCandidateOrAssigned(u);
					}
					taskQuery = taskQuery.endOr();
				}
			} else {
				taskQuery = taskQuery.taskCandidateOrAssigned(userId);
			}

		} else {
			if (replaceUsers != null) {
				taskQuery = taskQuery.taskAssigneeIds(replaceUsers);
			} else {
				taskQuery = taskQuery.taskAssignee(userId); // ??????????????????????????????????????????
			}
		}
		// ????????????????????????????????????or??????
		if (isQueryJobs) {
			taskQuery = taskQuery.endOr();
		}

		if (CheckDataUtil.isNotNull(processId)) {
			taskQuery = taskQuery.processDefinitionId(processId); // ??????????????????ID
		}
		if (CheckDataUtil.isNotNull(processKey)) {
			taskQuery = taskQuery.processDefinitionKey(processKey); // ??????????????????key
		}
		if (CheckDataUtil.isNotNull(processBusinessType)) {
			taskQuery = taskQuery.processDefinitionKeyLike("%\\" + ActivitiConstant.PROCESS_KEY_SPAN + processBusinessType
					+ "\\" + ActivitiConstant.PROCESS_KEY_SPAN + "%"); // ????????????????????????
		}
		if (CheckDataUtil.isNotNull(taskName)) {
			taskQuery = taskQuery.taskNameLike(taskName + "%"); // ????????????????????????
		}
		if (CheckDataUtil.isNotNull(taskDefId)) {
			taskQuery = taskQuery.taskDefinitionKey(taskDefId); // ????????????????????????ID
		}
		if (startTime != null) {
			taskQuery = taskQuery.taskCreatedAfter(startTime);
		}
		if (endTime != null) {
			taskQuery = taskQuery.taskCreatedBefore(endTime);
		}
		taskQuery = taskQuery.orderByTaskCreateTime().asc();
		List<Task> taskList = null;
		if (CheckDataUtil.isNotNull(processBusinessKeyList)) {
			taskList = new ArrayList<Task>();
			// ??????????????????key????????????????????????????????????
			for(String bKey : processBusinessKeyList)
			{
				taskList.addAll(taskQuery.processInstanceBusinessKey(bKey).list());
			}
		}
		else
		{
			// ????????????key?????????????????????????????????????????????
			taskQuery = taskQuery.processInstanceBusinessKey(ActivitiConstant.DEF_PROCESS_INST_BUSINESS_KEY); // ????????????key
		}
		List<String> list = new ArrayList<String>();
		int count = taskList == null ? (int) taskQuery.count() : taskList.size();
		if (count == 0) {
			return list;
		}
		if(taskList == null)
		{
			taskList = taskQuery.list();
		}
		for (Task task : taskList) {
			list.add(task.getProcessInstanceId());
		}
		return list;
	}

	@Override
	public PageList<TaskSampleData> getFinishedTasksByUser(String userId, String processId, String taskName,
														   Date startTime, Date endTime, int pageNum, int pageSize) throws WorkFlowException {
		return getFinishedTasksByUser(userId, processId, null, taskName, startTime, endTime, pageNum, pageSize);
	}

	@Override
	public PageList<TaskSampleData> getFinishedTasksByUser(String userId, String processId, String processKey,
														   String taskName, Date startTime, Date endTime, int pageNum, int pageSize) throws WorkFlowException {
		CheckDataUtil.checkNull(userId, "userId");
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}
		int firstResult = (pageNum - 1) * pageSize;
		int maxResults = pageSize;

		HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery().finished();

		// ???????????????????????????????????????
		List<String> replaceUsers = getReplaceUsers(userId, processId);
		if (replaceUsers != null) {
			replaceUsers.add(userId);
			taskQuery = taskQuery.taskAssigneeIds(replaceUsers);
		} else {
			taskQuery = taskQuery.taskAssignee(userId); // ??????????????????????????????????????????
		}

		if (CheckDataUtil.isNotNull(processId)) {
			taskQuery = taskQuery.processDefinitionId(processId); // ????????????ID
		}
		if (CheckDataUtil.isNotNull(processKey)) {
			taskQuery = taskQuery.processDefinitionKey(processKey); // ????????????Key
		}
		if (CheckDataUtil.isNotNull(taskName)) {
			taskQuery = taskQuery.taskNameLike(taskName + "%"); // ????????????????????????
		}
		if (startTime != null) {
			taskQuery = taskQuery.taskCreatedAfter(startTime);
		}
		if (endTime != null) {
			taskQuery = taskQuery.taskCreatedBefore(endTime);
		}
		taskQuery = taskQuery.orderByTaskCreateTime().desc();
		PageList<TaskSampleData> pageList = new PageList<TaskSampleData>();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal((int) taskQuery.count());
		if (pageList.getTotal() == 0) {
			return pageList;
		}

		List<HistoricTaskInstance> taskList = taskQuery.listPage(firstResult, maxResults);
		List<TaskSampleData> rows = new ArrayList<TaskSampleData>();
		for (HistoricTaskInstance task : taskList) {
			TaskSampleData taskSampleData = makeTaskSampleData(task);

			rows.add(taskSampleData);
		}
		pageList.setRows(rows);

		return pageList;
	}

	@Override
	public Map<String, List<TaskSampleData>> getFinishedTasks(String userId, String processId, String processKey,
															  String taskName, Date startTime, Date endTime) throws WorkFlowException {
		CheckDataUtil.checkNull(userId, "userId");

		HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery().finished();

		// ???????????????????????????????????????
		List<String> replaceUsers = getReplaceUsers(userId, processId);
		if (replaceUsers != null) {
			replaceUsers.add(userId);
			taskQuery = taskQuery.taskAssigneeIds(replaceUsers);
		} else {
			taskQuery = taskQuery.taskAssignee(userId); // ??????????????????????????????????????????
		}

		if (CheckDataUtil.isNotNull(processId)) {
			taskQuery = taskQuery.processDefinitionId(processId); // ????????????ID
		}
		if (CheckDataUtil.isNotNull(processKey)) {
			taskQuery = taskQuery.processDefinitionKey(processKey); // ????????????Key
		}
		if (CheckDataUtil.isNotNull(taskName)) {
			taskQuery = taskQuery.taskNameLike(taskName + "%"); // ????????????????????????
		}
		if (startTime != null) {
			taskQuery = taskQuery.taskCreatedAfter(startTime);
		}
		if (endTime != null) {
			taskQuery = taskQuery.taskCreatedBefore(endTime);
		}
		taskQuery = taskQuery.orderByTaskCreateTime().desc();


		List<HistoricTaskInstance> taskList = taskQuery.list();
		Map<String, List<TaskSampleData>> tasks = new HashMap<String, List<TaskSampleData>>();
		for (HistoricTaskInstance task : taskList) {
			TaskSampleData taskSampleData = new TaskSampleData();
			taskSampleData.setId(task.getId());
			taskSampleData.setTaskDefId(task.getTaskDefinitionKey());
			taskSampleData.setName(task.getName());
			taskSampleData.setCreateTime(task.getCreateTime());
			taskSampleData.setEndTime(task.getEndTime());
			taskSampleData.setProcessDefinitionId(task.getProcessDefinitionId());
			taskSampleData.setProcessInstanceId(task.getProcessInstanceId());
			taskSampleData.setExecutionId(task.getExecutionId());
			taskSampleData.setAssignee(task.getAssignee());
			if(!tasks.containsKey(taskSampleData.getProcessInstanceId()))
			{
				tasks.put(taskSampleData.getProcessInstanceId(), new ArrayList<TaskSampleData>());
			}
			tasks.get(taskSampleData.getProcessInstanceId()).add(taskSampleData);
		}
		return tasks;
	}

	@Override
	public PageList<TaskSampleData> getClaimTasksByUser(String userId, String processId, int pageNum, int pageSize)
			throws WorkFlowException {
		CheckDataUtil.checkNull(userId, "userId");
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}

		int firstResult = (pageNum - 1) * pageSize;
		int maxResults = pageSize;

		TaskQuery taskQuery = taskService // ???????????????????????????????????????Service
				.createTaskQuery() // ????????????????????????
				.taskCandidateUser(userId) // ????????????????????????
				.active(); // ?????????????????????????????????

		if (CheckDataUtil.isNotNull(processId)) {
			taskQuery = taskQuery.processDefinitionId(processId);
		}
		taskQuery = taskQuery.orderByTaskCreateTime().asc();
		PageList<TaskSampleData> pageList = new PageList<TaskSampleData>();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal((int) taskQuery.count());
		if (pageList.getTotal() == 0) {
			return pageList;
		}

		List<Task> taskList = taskQuery.listPage(firstResult, maxResults);
		List<TaskSampleData> rows = new ArrayList<TaskSampleData>();
		// ?????????????????????????????????????????????id??????
		Map<String, TaskSampleData> paramsCache = new HashMap<String, TaskSampleData>();
		for (Task task : taskList) {
			TaskSampleData taskSampleData = makeTaskSampleData(task, 2, false, paramsCache);

			rows.add(taskSampleData);
		}

		pageList.setRows(rows);

		return pageList;
	}

	@Override
	public PageList<TaskSampleData> getClaimTasksByUnit(String unitId, String processId, int pageNum, int pageSize)
			throws WorkFlowException {

		CheckDataUtil.checkNull(unitId, "unitId");
		if (pageNum < 1) {
			pageNum = 1;
		}
		if (pageSize < 1) {
			pageSize = 1;
		}

		int firstResult = (pageNum - 1) * pageSize;
		int maxResults = pageSize;

		TaskQuery taskQuery = taskService // ???????????????????????????????????????Service
				.createTaskQuery() // ????????????????????????
				.taskCandidateGroup(unitId) // ????????????????????????
				.active(); // ?????????????????????????????????

		if (CheckDataUtil.isNotNull(processId)) {
			taskQuery = taskQuery.processDefinitionId(processId);
		}
		taskQuery = taskQuery.orderByTaskCreateTime().asc();
		PageList<TaskSampleData> pageList = new PageList<TaskSampleData>();
		pageList.setPageNum(pageNum);
		pageList.setPageSize(pageSize);
		pageList.setTotal((int) taskQuery.count());
		if (pageList.getTotal() == 0) {
			return pageList;
		}

		List<Task> taskList = taskQuery.listPage(firstResult, maxResults);
		List<TaskSampleData> rows = new ArrayList<TaskSampleData>();
		// ?????????????????????????????????????????????id??????
		Map<String, TaskSampleData> paramsCache = new HashMap<String, TaskSampleData>();
		for (Task task : taskList) {
			TaskSampleData taskSampleData = makeTaskSampleData(task, 2, false, paramsCache);
			rows.add(taskSampleData);
		}
		pageList.setRows(rows);

		return pageList;
	}

	@Override
	public TaskData getTaskInfo(String taskId) throws WorkFlowException {
		CheckDataUtil.checkNull(taskId, "taskId");
		Task task = findTask(taskId);
		int stateType = 1;
		if (task.getAssignee() == null) {
			stateType = 2;
		}
		return makeTaskData(task, stateType);
	}

	@Override
	public TaskData getUserTaskInfoByInstance(String userId, String processInstanceId) throws WorkFlowException {
		return getUserTaskInfoByInstance(userId, null, null, null, processInstanceId);
	}

	@Override
	public TaskData getUserTaskInfoByInstance(String userId, String region, String unitId, List<String> jobIdList,
											  String processInstanceId) throws WorkFlowException {
		List<String> unitIds = null;
		if (CheckDataUtil.isNotNull(unitId)) {
			unitIds = new ArrayList<String>();
			unitIds.add(unitId);
		}
		return getUserTaskInfoByInstance(userId, region, unitIds, null, jobIdList, processInstanceId);
	}

	@Override
	public TaskData getUserTaskInfoByInstance(String userId, String region, List<String> unitIds,
											  List<String> adminUnitIds, List<String> jobIdList, String processInstanceId) throws WorkFlowException {
		CheckDataUtil.checkNull(userId, "userId");
		CheckDataUtil.checkNull(processInstanceId, "processInstanceId");
		TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(processInstanceId);

		boolean isQueryJobs = false;
		List<String> claimGroupList = new ArrayList<String>();
		// ?????????????????????????????????
		if (CheckDataUtil.isNotNull(jobIdList)) {
			isQueryJobs = true;
			taskQuery = taskQuery.or();
			claimGroupList.addAll(jobIdList);
			// ???????????????????????????
			if (CheckDataUtil.isNotNull(unitIds)) {
				for (String jobId : jobIdList) {
					for (String unitId : unitIds) {
						String claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
								+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
								+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
						claimGroupList.add(claimGroup);
					}
				}
			}
			// ????????????????????????????????????
			if (CheckDataUtil.isNotNull(adminUnitIds)) {
				for (String jobId : jobIdList) {
					for (String unitId : adminUnitIds) {
						String claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
								+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_ADMIN_UNIT
								+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
						claimGroupList.add(claimGroup);
						// ???????????????????????????
						claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
								+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
								+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
						claimGroupList.add(claimGroup);
					}
				}
			}
			// ???????????????????????????
			if (CheckDataUtil.isNotNull(region)) {
				for (String jobId : jobIdList) {
					String claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
							+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_REGION + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
							+ region;
					claimGroupList.add(claimGroup);
				}
			}

		}
		// ???????????????????????????
		if (CheckDataUtil.isNotNull(unitIds)) {
			if (!isQueryJobs) {
				isQueryJobs = true;
				taskQuery = taskQuery.or();
			}
			for (String unitId : unitIds) {
				String claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
						+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
				claimGroupList.add(claimGroup);
			}
		}
		// ????????????????????????????????????
		if (CheckDataUtil.isNotNull(adminUnitIds)) {
			if (!isQueryJobs) {
				isQueryJobs = true;
				taskQuery = taskQuery.or();
			}
			for (String unitId : adminUnitIds) {
				String claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_ADMIN_UNIT
						+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
				claimGroupList.add(claimGroup);
				// ???????????????????????????
				claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
						+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
				claimGroupList.add(claimGroup);
			}
		}
		// ???????????????????????????
		if (CheckDataUtil.isNotNull(region)) {
			if (!isQueryJobs) {
				isQueryJobs = true;
				taskQuery = taskQuery.or();
			}
			String claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_REGION
					+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + region;
			claimGroupList.add(claimGroup);
		}
		if (!claimGroupList.isEmpty()) {
			taskQuery = taskQuery.taskCandidateGroupIn(claimGroupList);
		}

		taskQuery = taskQuery.taskCandidateOrAssigned(userId);

//		//???????????????????????????????????????
//		List<String> replaceUsers = getReplaceUsers(userId, processId);
//		if(replaceUsers != null)
//		{
//			replaceUsers.add(userId);
//		}
//		//???????????????????????????
//		if(replaceUsers != null)
//		{
//			taskQuery = taskQuery.or();
//			for(String u : replaceUsers)
//			{
//				taskQuery = taskQuery.taskCandidateOrAssigned(u);
//			}
//			taskQuery = taskQuery.endOr();
//		}
//		else
//		{
//			taskQuery = taskQuery.taskCandidateOrAssigned(userId);
//		}

		// ????????????????????????????????????or??????
		if (isQueryJobs) {
			taskQuery = taskQuery.endOr();
		}

		Task task = taskQuery.singleResult();

		return makeTaskData(task, 1);
	}

	@Override
	public TaskData getFinishedTaskInfo(String taskId) throws WorkFlowException {
		CheckDataUtil.checkNull(taskId, "taskId");
		HistoricTaskInstance task = historyService.createHistoricTaskInstanceQuery().finished().taskId(taskId)
				.singleResult();
		return makeTaskData(task);
	}

	@Override
	public List<TaskDefData> getProcessInfoByTask(String taskId) throws WorkFlowException {
		CheckDataUtil.checkNull(taskId, "taskId");
		Task task = findTask(taskId);
		if (task == null) {
			return null;
		}
		return getProcessInfoByDefId(task.getProcessDefinitionId());
	}

	@Override
	public List<TaskDefData> getProcessInfoByDefId(String processDefinitionId) throws WorkFlowException {
		CheckDataUtil.checkNull(processDefinitionId, "processDefinitionId");
		List<TaskDefData> list = new ArrayList<TaskDefData>();
		BpmnModel model = repositoryService.getBpmnModel(processDefinitionId);
		if (model != null) {

			Collection<FlowElement> flowElements = model.getMainProcess().getFlowElements();
			for (FlowElement e : flowElements) {
				// System.out.println("flowelement id:" + e.getId() + " name:" +
				// e.getName() + " class:" + e.getClass().toString());
				// logger.debug(e.getName() + " ------ " + e.getDocumentation());
				String taskid = e.getId();
				if (taskid != null) {
					String[] names = taskid.split(ActivitiConstant.PROCESS_TASK_ID_SPAN);
					String type = names[0];
					if(ActivitiConstant.USERTASK_HEAD.equals(type))
					{
						TaskDefData taskDefData = new TaskDefData();
						taskDefData.setId(e.getId());
						taskDefData.setName(e.getName());
						int sort = 0;
						if(names.length > 1)
						{
							try {
								sort = Integer.parseInt(names[1]);
							} catch (NumberFormatException ex) {

							}
						}
						taskDefData.setSort(sort);
						list.add(taskDefData);
					}
				}
			}
		}
		if(CheckDataUtil.isNotNull(list))
		{
			//????????????
			list.sort(ActivitiServiceImpl::TaskDefSort);
		}
		return list;
	}

	/**
	 * @Title: TaskDefSort
	 * @Description: ????????????
	 * @param a
	 * @param b
	 * @return  ????????????
	 * @return int    ????????????
	 *
	 */
	public static int TaskDefSort(TaskDefData a, TaskDefData b)
	{
		if(a.getSort() < b.getSort())
			return -1;
		return 1;
	}

	@Override
	@Transactional
	public boolean updateTaskValues(String userId, String taskId, Map<String, String> values,
									Map<String, List<String>> signUsers, Map<String, List<String>> claimUsers) throws WorkFlowException {
		return updateTaskValues(userId, taskId, values, signUsers, claimUsers, null);
	}

	@Override
	public boolean updateTaskValues(String userId, String taskId, Map<String, String> values,
									Map<String, List<String>> signUsers, Map<String, List<String>> claimUsers, Map<String, String> exValues)
			throws WorkFlowException {
		CheckDataUtil.checkNull(taskId, "taskId");
		if (signUsers != null) {
			taskService.setVariables(taskId, signUsers);
		}
		if (claimUsers != null) {
			taskService.setVariables(taskId, claimUsers);
		}
		if (exValues != null) {
			saveTaskExValues(taskId, exValues);
		}
		if (values != null) {
			formService.saveFormData(taskId, values);
			// ????????????????????????????????????
			// taskService.setVariablesLocal(taskId, values);
		}
		return true;
	}

	@Override
	@Transactional
	public boolean complateTask(String userId, String taskId, Map<String, String> values,
								Map<String, List<String>> signUsers, Map<String, List<String>> claimUsers) throws WorkFlowException {
		return complateTask(userId, null, null, taskId, values, signUsers, claimUsers, null);
	}

	@Override
	@Transactional
	public boolean complateTask(String userId, String taskId, Map<String, String> values,
								Map<String, List<String>> signUsers, Map<String, List<String>> claimUsers, Map<String, String> exValues)
			throws WorkFlowException {
		return complateTask(userId, null, null, taskId, values, signUsers, claimUsers, exValues);
	}

	@Override
	@Transactional
	public boolean complateTask(String userId, String region, String unitId, String taskId, Map<String, String> values,
								Map<String, List<String>> signUsers, Map<String, List<String>> claimUsers, Map<String, String> exValues)
			throws WorkFlowException {
		return complateTask(userId, region, unitId, taskId, values, signUsers, claimUsers, null, exValues);
	}

	@Override
	@Transactional
	public boolean complateTask(String userId, String region, String unitId, String taskId, Map<String, String> values,
								Map<String, List<String>> signUsers, Map<String, List<String>> claimUsers,
								Map<String, List<String>> claimGroups, Map<String, String> exValues) throws WorkFlowException {
		List<String> unitIds = null;
		if (CheckDataUtil.isNotNull(unitId)) {
			unitIds = new ArrayList<String>();
			unitIds.add(unitId);
		}
		return complateTask(userId, region, unitIds, taskId, values, signUsers, claimUsers, claimGroups, exValues);
	}

	@Override
	@Transactional
	public boolean complateTask(String userId, String region, List<String> unitIds, String taskId,
								Map<String, String> values, Map<String, List<String>> signUsers, Map<String, List<String>> claimUsers,
								Map<String, List<String>> claimGroups, Map<String, String> exValues) throws WorkFlowException {
		CheckDataUtil.checkNull(userId, "userId");
		CheckDataUtil.checkNull(taskId, "taskId");
		// ????????????
		Task task = findTask(taskId);
		if (task == null) {
			throw new WorkFlowException("60501", "???????????????");
		}

		if (signUsers != null) {
			taskService.setVariables(taskId, signUsers);
		}
		if (claimUsers != null) {
			taskService.setVariables(taskId, claimUsers);
		}
		if (exValues != null) {
			saveTaskExValues(taskId, exValues);
		}
		if (values != null) {
			formService.saveFormData(taskId, values);
			// ????????????????????????????????????
			// taskService.setVariablesLocal(taskId, values);
		}

		// ??????????????????????????????????????????????????????????????????????????????
		TaskFormData fdata = formService.getTaskFormData(taskId);
		if (fdata != null) {
			Map<String, List<String>> cgs = new HashMap<String, List<String>>();
			List<FormProperty> fvalues = fdata.getFormProperties();
			for (FormProperty fvalue : fvalues) {
				String id = fvalue.getId();
				String[] propertyTypes = id.split(ActivitiConstant.FORM_PROPERTY_KEY_SPAN);
				String propertyType = propertyTypes[0];
				switch (propertyType) {
					case ActivitiConstant.CLAIM_GROUPS_FORM_ID_HEAD: {
						if (propertyTypes.length < 2) {
							logger.warn("?????????????????????????????????????????????" + id);
							continue;
						}
						// ????????????
						String cgType = propertyTypes[1];
						// ??????id??????
						List<String> targetIds = new ArrayList<String>();
						switch (cgType) {
							case ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT: // ????????????
							case ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_ADMIN_UNIT: // ?????????????????????
							{
								// ????????????????????????????????????????????????
								if (!CheckDataUtil.isNotNull(unitIds)) {
									throw new WorkFlowException("60504", "???????????????????????????");
								}
								targetIds = unitIds;
							}
							break;
							case ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_REGION: // ????????????
							{
								// ????????????????????????????????????????????????
								if (!CheckDataUtil.isNotNull(region)) {
									throw new WorkFlowException("60504", "???????????????????????????");
								}
								targetIds.add(region);
							}
							break;
						}
						if (targetIds.isEmpty()) {
							logger.warn("???????????????????????????????????????" + id);
							continue;
						}
						// ??????????????????
						String[] groupKeys = fvalue.getValue().split(ActivitiConstant.FORM_PROPERTY_KEY_SPAN);
						String groupsKey = groupKeys[0]; // ???????????????????????????key
						String groupsDefKey = null;
						if (groupKeys.length == 2) {
							groupsDefKey = groupKeys[1]; // ????????????????????????????????????key
						}
						// ??????????????????????????????
						if (groupsDefKey != null) {
							Object defgroups = taskService.getVariable(task.getId(), groupsDefKey);
							if (defgroups != null) {
								// ??????????????????????????????
								List<String> groupList = new ArrayList<String>();
								String[] groupidlist = defgroups.toString().split(",");
								for (String groupid : groupidlist) {
									// ????????????ID=??????id+??????+??????id????????? ???_??????
									for (String targetId : targetIds) {
										String newgroupid = groupid + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + cgType
												+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + targetId;
										groupList.add(newgroupid);
									}
								}
								cgs.put(groupsKey, groupList);
							}
						} else {
							List<String> groupList = new ArrayList<String>();
							// ????????????????????? ??????+??????id
							for (String targetId : targetIds) {
								String newgroupid = cgType + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + targetId;
								groupList.add(newgroupid);
							}
							cgs.put(groupsKey, groupList);
						}
					}
					break;
					case ActivitiConstant.CLAIM_GROUPS_VALUE_FORM_ID_HEAD: {
						if (propertyTypes.length < 2) {
							logger.warn("?????????????????????????????????????????????" + id);
							continue;
						}
						// ????????????
						String cgType = propertyTypes[1];
						String keysstr = fvalue.getValue();
						String[] keys = keysstr.split(ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN);
						String paramkey = keys[0]; // ????????????key
						String defkey = null; // ????????????????????????key
						if (keys.length > 1) {
							defkey = keys[1];
						}
						// ???????????????????????????
						if (!claimGroups.containsKey(paramkey)) {
							throw new WorkFlowException("60504", "??????????????????????????????" + paramkey);
						}
						// ?????????
						List<String> cglist = claimGroups.get(paramkey);
						if (cglist == null || cglist.isEmpty()) {
							throw new WorkFlowException("60504", "?????????????????????" + paramkey);
						}
						List<String> groupList = new ArrayList<String>();
						Object defgroups = null;
						if (defkey != null) {
							defgroups = taskService.getVariable(task.getId(), defkey);
						}
						// ????????????????????? ??????+??????id
						if (defgroups == null) {
							for (String cg : cglist) {
								String newgroupid = cgType + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + cg;
								groupList.add(newgroupid);
							}
						} else {
							String[] groupidlist = defgroups.toString().split(",");
							for (String groupid : groupidlist) {
								for (String cg : cglist) {
									// ????????????ID=??????id+??????+??? ???_??????
									String newgroupid = groupid + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + cgType
											+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + cg;
									groupList.add(newgroupid);
								}
							}
						}
						if (!groupList.isEmpty()) {
							cgs.put(paramkey, groupList);
						}
					}
					break;
					case ActivitiConstant.CLAIM_GROUPS_DATA_FORM_ID_HEAD:
					{
						String keysstr = fvalue.getValue();
						String[] keys = keysstr.split(ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN);
						String paramkey = keys[0]; // ????????????key
						// ???????????????????????????
						if (!claimGroups.containsKey(paramkey)) {
							throw new WorkFlowException("60504", "??????????????????????????????" + paramkey);
						}
						// ?????????
						List<String> groupList = claimGroups.get(paramkey);

						if (CheckDataUtil.isNotNull(groupList)) {
							cgs.put(paramkey, groupList);
						}
					}
					case ActivitiConstant.LOOP_COUNT_HEAD:		// ??????????????????
					{
						String loopcountkey = fvalue.getValue();
						// ???????????????
						Object olv = taskService.getVariable(taskId, loopcountkey);
						int loopcount = olv == null ? 0 : Integer.parseInt(String.valueOf(olv));
						++loopcount;
						// ?????????????????????
						taskService.setVariable(taskId, loopcountkey, loopcount);
					}
					break;
					default: {

					}
					break;
				}

			}
			if (!cgs.isEmpty()) {
				taskService.setVariables(taskId, cgs);
			}
		}
		;

		saveTaskLocalValues(taskId, fdata);
		//?????????????????????????????????taskid
		if(userTaskFinishedService.getStcsmUserTaskFinishedByTaskId(taskId)!=null){
			//??????????????????????????????
			logger.info("-----add usertaskfinished Data duplication : "+taskId);
			userTaskFinishedService.remove(taskId);
		}
		// ?????????
		UserInfo userInfo = loadUserInfo(userId, true);
		UserTaskFinishedEntity taskFinished = new UserTaskFinishedEntity();
		taskFinished.setProcessId(task.getProcessDefinitionId());
		taskFinished.setProcessKey(getProcessKeyById(task.getProcessDefinitionId()));
		taskFinished.setProcessName(getProcessDefName(task.getProcessDefinitionId()));
		taskFinished.setProcessInstanceId(task.getProcessInstanceId());
		taskFinished.setTaskId(taskId);
		taskFinished.setTaskName(task.getName());
		taskFinished.setUnitId(userInfo.getUnitId());
		taskFinished.setUnitName(userInfo.getUnitName());
		taskFinished.setTaskDefId(task.getTaskDefinitionKey());
		taskFinished.setUserId(userInfo.getId());
		taskFinished.setUserName(userInfo.getNick());
		taskFinished.setFinishTime(new Date());
		userTaskFinishedService.saveStcsmUserTaskFinished(taskFinished);
		// ?????????
		// ???????????????????????????
		jedisMgrWf.pushIncrSortSet(getUserFinishedKey(), userId, 1d);
		// ???????????????????????????
		if (taskFinished.getUnitId() != null) {
			jedisMgrWf.pushIncrSortSet(getUnitFinishedKey(), taskFinished.getUnitId(), 1d);
		}

		// ???????????????
		if (!setTaskAssignee(userId, taskId)) {
			return false;
		}
		//?????????????????????????????? ?????????????????????-??????????????????-??????
		boolean isreturnUsertaskfinished=false;
		try {
			taskService.complete(taskId);
		} catch (ActivitiObjectNotFoundException e) {
			isreturnUsertaskfinished=true;
			throw new WorkFlowException("60501", "???????????????");
		} catch (ActivitiException e) {
			isreturnUsertaskfinished=true;
			throw new WorkFlowException("60501", "???????????????????????????????????????");
		}
		//???????????????
		if(isreturnUsertaskfinished){
			logger.info("-----return usertaskfinished complete failure : "+taskId);
			userTaskFinishedService.remove(taskId);
		}
		return true;
	}

	@Override
	@Transactional
	public boolean complateTasks(String userId, String region, List<String> unitIds, List<String> taskIdList,
								 Map<String, String> values, Map<String, List<String>> signUsers, Map<String, List<String>> claimUsers,
								 Map<String, List<String>> claimGroups, Map<String, String> exValues) throws WorkFlowException {
		for (String taskId : taskIdList) {
			if (!complateTask(userId, region, unitIds, taskId, values, signUsers, claimUsers, claimGroups, exValues)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @Title: saveTaskLocalValues
	 * @Description: ????????????????????????--?????????????????????????????????
	 * @param taskId ????????????
	 * @return void ????????????
	 *
	 */
	public void saveTaskLocalValues(String taskId) {
		saveTaskLocalValues(taskId, null);
	}

	/**
	 * @Title: saveTaskLocalValues
	 * @Description: ????????????????????????--?????????????????????????????????
	 * @param taskId
	 * @param fdata  ????????????
	 * @return void ????????????
	 *
	 */
	@Transactional
	public void saveTaskLocalValues(String taskId, TaskFormData fdata) {
		if (fdata == null) {
			fdata = formService.getTaskFormData(taskId);
			if (fdata == null) {
				return;
			}
		}

		// ??????????????????????????????????????????--??????????????????URL???????????????
		Map<String, Object> variables = new HashMap<String, Object>();
		List<FormProperty> fvalues = fdata.getFormProperties();
		for (FormProperty fvalue : fvalues) {
			String id = fvalue.getId();
			String[] propertyTypes = id.split(ActivitiConstant.FORM_PROPERTY_KEY_SPAN);
			String propertyType = propertyTypes[0];

			switch (propertyType) {
				case ActivitiConstant.BUSINESS_KEY: // ??????URL
				{
					variables.put(ActivitiConstant.BUSINESS_KEY, fvalue.getValue());
				}
				break;
				case ActivitiConstant.EXPIRED_KEY: // ??????????????????
				{
					variables.put(ActivitiConstant.EXPIRED_KEY, fvalue.getValue());
				}
				break;
				case ActivitiConstant.RETRIEVE_KEY: // ??????????????????
				{
					variables.put(ActivitiConstant.RETRIEVE_KEY, fvalue.getValue());
				}
				break;
				case ActivitiConstant.SMARTFORM_FORM_ID_HEAD: // ??????
				{
					variables.put(propertyType + ActivitiConstant.LOCAL_PROPERTY_KEY_SPAN + propertyTypes[1]
							+ ActivitiConstant.LOCAL_PROPERTY_KEY_SPAN + fvalue.getName(), fvalue.getValue());
				}
				break;
				case ActivitiConstant.SIGN_USERS_FORM_ID_HEAD: // ????????????
				{
					String[] userKeys = fvalue.getValue().split(ActivitiConstant.FORM_PROPERTY_KEY_SPAN);
					String usersKey = userKeys[0]; // ???????????????????????????key
					Object users = taskService.getVariable(taskId, usersKey);
					variables.put(propertyType + ActivitiConstant.LOCAL_PROPERTY_KEY_SPAN + usersKey
							+ ActivitiConstant.LOCAL_PROPERTY_KEY_SPAN + fvalue.getName(), users);
				}
				break;
				case ActivitiConstant.CLAIM_USERS_FORM_ID_HEAD: // ????????????
				{
					String[] userKeys = fvalue.getValue().split(ActivitiConstant.FORM_PROPERTY_KEY_SPAN);
					String usersKey = userKeys[0]; // ???????????????????????????key
					Object users = taskService.getVariable(taskId, usersKey);
					variables.put(propertyType + ActivitiConstant.LOCAL_PROPERTY_KEY_SPAN + usersKey
							+ ActivitiConstant.LOCAL_PROPERTY_KEY_SPAN + fvalue.getName(), users);
				}
				break;
				case ActivitiConstant.CLAIM_GROUPS_FORM_ID_HEAD: // ????????????--??????????????????
				case ActivitiConstant.CLAIM_GROUPS_VALUE_FORM_ID_HEAD: // ?????????????????????
				case ActivitiConstant.CLAIM_GROUPS_DATA_FORM_ID_HEAD: // ????????????--????????????
				{
					String[] groupKeys = fvalue.getValue().split(ActivitiConstant.FORM_PROPERTY_KEY_SPAN);
					String groupsKey = groupKeys[0]; // ???????????????????????????key
					Object users = taskService.getVariable(taskId, groupsKey);
					variables.put(propertyType + ActivitiConstant.LOCAL_PROPERTY_KEY_SPAN + groupsKey
							+ ActivitiConstant.LOCAL_PROPERTY_KEY_SPAN + fvalue.getName(), users);
				}
				break;
				default: // ???????????????
				{
					variables.put(propertyType + ActivitiConstant.LOCAL_PROPERTY_KEY_SPAN + id
							+ ActivitiConstant.LOCAL_PROPERTY_KEY_SPAN + fvalue.getName(), fvalue.getValue());
				}
				break;
			}
		}
		taskService.setVariablesLocal(taskId, variables);
		return;
	}

	@Override
	@Transactional
	public boolean claimTask(String userId, String taskId) throws WorkFlowException {
		CheckDataUtil.checkNull(userId, "userId");
		CheckDataUtil.checkNull(taskId, "taskId");
		try {
			taskService.claim(taskId, userId);
		} catch (ActivitiObjectNotFoundException e) {
			throw new WorkFlowException("60501", "???????????????");
		} catch (ActivitiTaskAlreadyClaimedException e) {
			throw new WorkFlowException("60501", "??????????????????");
		}

		return true;
	}

	@Override
	@Transactional
	public boolean retrieveTask(String userId, String taskId, Map<String, String> exValues) throws WorkFlowException {
		CheckDataUtil.checkNull(userId, "userId");
		CheckDataUtil.checkNull(taskId, "taskId");
		HistoricTaskInstance task = historyService.createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
		if (task == null) {
			throw new WorkFlowException("60501", "???????????????");
		}

		if (!task.getAssignee().equals(userId)) {
			// ???????????????????????????????????????
			List<String> replaceUsers = getReplaceUsers(userId, task.getProcessDefinitionId());
			if (replaceUsers == null || replaceUsers.indexOf(task.getAssignee()) < 0) {
				throw new WorkFlowException("60501", "???????????????????????????????????????");
			}
		}

		HistoricVariableInstance value = historyService.createHistoricVariableInstanceQuery().taskId(task.getId())
				.variableName(ActivitiConstant.RETRIEVE_KEY).singleResult();
		if (value == null) {
			throw new WorkFlowException("60501", "?????????????????????");
		}
		String[] values = value.getValue().toString().split(ActivitiConstant.RETRIEVE_PROPERTY_VALUE_SPAN);
//		// ????????????????????????id????????????????????????
//		String retrieveTaskDefIds = values[0];
//		if(!canRetrieveTask(task.getProcessInstanceId(), retrieveTaskDefIds))
//		{
//			throw new WorkFlowException("60501", "??????????????????????????????");
//		}
		if(values.length < 2)
		{
			throw new WorkFlowException("60501", "????????????????????????????????????????????? value:" + value.getValue().toString());
		}
		// ?????????????????????
		String signalName = values[1];
		Execution execution = runtimeService.createExecutionQuery().signalEventSubscriptionName(signalName)
				.rootProcessInstanceId(task.getProcessInstanceId()).singleResult();
		if (execution == null) {
			throw new WorkFlowException("60501", "??????????????????????????????");
		}
		if(values.length == 2)
		{
			runtimeService.signalEventReceived(signalName, execution.getId());
		}
		else
		{
			String singleTaskDefId = values[2];
			Task singleTask = taskService.createTaskQuery().processInstanceId(task.getProcessInstanceId()).taskDefinitionKey(singleTaskDefId).singleResult();
			if(singleTask == null)
			{
				throw new WorkFlowException("60501", "????????????????????????????????????????????? value:" + value.getValue().toString());
			}
			taskService.complete(singleTask.getId());
		}
		// runtimeService.dispatchEvent(activitiEvent);
		return true;
//		Map<String, String> params = new HashMap<String, String>();
//		params.put(retrieveParam, "1");
//		return complateTask(userId, retrieveTask.getId(), params, null, null, exValues);
	}


	@Override
	@Transactional
	public boolean jumpTask(String startTaskId, String targetTaskDefId) throws WorkFlowException {
		CheckDataUtil.checkNull(startTaskId, "startTaskId");
		CheckDataUtil.checkNull(targetTaskDefId, "targetTaskDefId");

		Task startTask = taskService.createTaskQuery().active().taskId(startTaskId).singleResult();
		if(startTask == null)
		{
			throw new WorkFlowException("?????????????????????????????????????????????");
		}
		jump(startTask, targetTaskDefId);
		return true;
	}

	@Override
	public boolean jumpTaskByProInst(String processInstId, String targetTaskDefId) throws WorkFlowException {
		CheckDataUtil.checkNull(processInstId, "startTaskId");
		CheckDataUtil.checkNull(targetTaskDefId, "targetTaskDefId");

		Task startTask = taskService.createTaskQuery().active().processInstanceId(processInstId).singleResult();
		if(startTask == null)
		{
			throw new WorkFlowException("?????????????????????????????????????????????");
		}
		jump(startTask, targetTaskDefId);
		return true;
	}

	@Override
	public boolean setTaskAssignee(String userId, String taskId) throws WorkFlowException {
		CheckDataUtil.checkNull(userId, "userId");
		CheckDataUtil.checkNull(taskId, "taskId");
		try {
			taskService.setAssignee(taskId, userId);
		} catch (ActivitiObjectNotFoundException e) {
			throw new WorkFlowException("60501", "???????????????");
		}

		return true;
	}

	@Override
	public List<TaskSampleData> getTasksByProcessInstanceID(String processInstanceId) throws WorkFlowException {
		CheckDataUtil.checkNull(processInstanceId, "processInstanceId");
		HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery()
				.processInstanceId(processInstanceId);
		taskQuery = taskQuery.orderByTaskCreateTime().desc();
		List<HistoricTaskInstance> taskList = taskQuery.list();

		List<TaskSampleData> rows = new ArrayList<TaskSampleData>();
		for (HistoricTaskInstance task : taskList) {
			TaskSampleData taskSampleData = makeTaskSampleData(task);
			rows.add(taskSampleData);
		}
		return rows;
	}

	@Override
	public List<TaskSampleData> getActiveTasksByProcessInstanceID(String processInstanceId) throws WorkFlowException {
		CheckDataUtil.checkNull(processInstanceId, "processInstanceId");
		TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(processInstanceId);
		taskQuery = taskQuery.orderByTaskCreateTime().desc();
		List<Task> taskList = taskQuery.list();
		List<TaskSampleData> rows = new ArrayList<TaskSampleData>();
		// ?????????????????????????????????????????????id??????
		Map<String, TaskSampleData> paramsCache = new HashMap<String, TaskSampleData>();
		for (Task task : taskList) {
			int stateType = 1;
			if (task.getAssignee() == null) {
				stateType = 2;
			}
			TaskSampleData taskSampleData = makeTaskSampleData(task, stateType, false, paramsCache);
			rows.add(taskSampleData);
		}
		return rows;
	}

	@Override
	public List<TaskData> getTaskDatasByProcessInstanceID(String processInstanceId) throws WorkFlowException {
		CheckDataUtil.checkNull(processInstanceId, "processInstanceId");
		HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery()
				.processInstanceId(processInstanceId);
		taskQuery = taskQuery.orderByTaskCreateTime().desc();
		List<HistoricTaskInstance> taskList = taskQuery.list();

		List<TaskData> rows = new ArrayList<TaskData>();
		for (HistoricTaskInstance task : taskList) {
			TaskData taskData = makeTaskData(task);
			rows.add(taskData);
		}
		return rows;
	}

	@Override
	public Map<String, Map<String, TasksInfo>> getTasksInfoByUser(String userId, boolean showClaim)
			throws WorkFlowException {

		return getTasksInfoByUser(userId, null, null, null, showClaim);
	}

	@Override
	public Map<String, Map<String, TasksInfo>> getTasksInfoByUser(String userId, String region, String unitId,
																  List<String> jobIdList, boolean showClaim) throws WorkFlowException {
		List<String> unitIds = null;
		if (CheckDataUtil.isNotNull(unitId)) {
			unitIds = new ArrayList<String>();
			unitIds.add(unitId);
		}
		return getTasksInfoByUser(userId, region, unitIds, null, jobIdList, showClaim);
	}

	@Override
	public Map<String, Map<String, TasksInfo>> getTasksInfoByUser(String userId, String region, List<String> unitIds,
																  List<String> adminUnitIds, List<String> jobIdList, boolean showClaim) throws WorkFlowException {
		return getTasksInfoByUser(userId, region, unitIds, adminUnitIds, jobIdList, null, showClaim);
	}

	@Override
	public Map<String, Map<String, TasksInfo>> getTasksInfoByUser(String userId, String region, List<String> unitIds,
																  List<String> adminUnitIds, List<String> jobIdList, List<String> businessTypeList, boolean showClaim)
			throws WorkFlowException {
		return getTasksInfoByUser(userId, region, unitIds, adminUnitIds, jobIdList, businessTypeList, null, showClaim);
	}

	@Override
	public Map<String, Map<String, TasksInfo>> getTasksInfoByUser(String userId, String region, List<String> unitIds,
																  List<String> adminUnitIds, List<String> jobIdList, List<String> businessTypeList, List<String> processBusinessKeyList,
																  boolean showClaim) throws WorkFlowException {
		CheckDataUtil.checkNull(userId, "userId");
		TaskQuery taskQuery = taskService // ???????????????????????????????????????Service
				.createTaskQuery() // ????????????????????????
				.active(); // ?????????????????????????????????
		boolean isQueryJobs = false;
		List<String> claimGroupList = new ArrayList<String>();
		// ?????????????????????????????????
		if (CheckDataUtil.isNotNull(jobIdList)) {
			isQueryJobs = true;
			taskQuery = taskQuery.or();
			claimGroupList.addAll(jobIdList);
			// ???????????????????????????
			if (CheckDataUtil.isNotNull(unitIds)) {
				for (String jobId : jobIdList) {
					for (String unitId : unitIds) {
						String claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
								+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
								+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
						claimGroupList.add(claimGroup);
					}
				}
			}
			// ????????????????????????????????????
			if (CheckDataUtil.isNotNull(adminUnitIds)) {
				for (String jobId : jobIdList) {
					for (String unitId : adminUnitIds) {
						String claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
								+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_ADMIN_UNIT
								+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
						claimGroupList.add(claimGroup);
						// ???????????????????????????
						claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
								+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
								+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
						claimGroupList.add(claimGroup);
					}
				}
			}
			// ???????????????????????????
			if (CheckDataUtil.isNotNull(region)) {
				for (String jobId : jobIdList) {
					String claimGroup = jobId + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
							+ ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_REGION + ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN
							+ region;
					claimGroupList.add(claimGroup);
				}
			}

		}
		// ???????????????????????????
		if (CheckDataUtil.isNotNull(unitIds)) {
			if (!isQueryJobs) {
				isQueryJobs = true;
				taskQuery = taskQuery.or();
			}
			for (String unitId : unitIds) {
				String claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
						+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
				claimGroupList.add(claimGroup);
			}
		}
		// ????????????????????????????????????
		if (CheckDataUtil.isNotNull(adminUnitIds)) {
			if (!isQueryJobs) {
				isQueryJobs = true;
				taskQuery = taskQuery.or();
			}
			for (String unitId : adminUnitIds) {
				String claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_ADMIN_UNIT
						+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
				claimGroupList.add(claimGroup);
				// ???????????????????????????
				claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_UNIT
						+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + unitId;
				claimGroupList.add(claimGroup);
			}
		}
		// ???????????????????????????
		if (CheckDataUtil.isNotNull(region)) {
			if (!isQueryJobs) {
				isQueryJobs = true;
				taskQuery = taskQuery.or();
			}
			String claimGroup = ActivitiConstant.CLAIM_GROUPS_FORM_TYPE_REGION
					+ ActivitiConstant.CLAIM_GROUPS_VALUE_SPAN + region;
			claimGroupList.add(claimGroup);
		}
		if (!claimGroupList.isEmpty()) {
			taskQuery = taskQuery.taskCandidateGroupIn(claimGroupList);
		}
		// ???????????????????????????????????????
		List<String> replaceUsers = getReplaceUsers(userId, null);
		if (replaceUsers != null) {
			replaceUsers.add(userId);
		}
		// ???????????????????????????
		if (showClaim) {
			if (replaceUsers != null) {
				if (isQueryJobs) {
					for (String u : replaceUsers) {
						taskQuery = taskQuery.taskCandidateOrAssigned(u);
					}
				} else {
					taskQuery = taskQuery.or();
					for (String u : replaceUsers) {
						taskQuery = taskQuery.taskCandidateOrAssigned(u);
					}
					taskQuery = taskQuery.endOr();
				}
			} else {
				taskQuery = taskQuery.taskCandidateOrAssigned(userId);
			}

		} else {
			if (replaceUsers != null) {
				taskQuery = taskQuery.taskAssigneeIds(replaceUsers);
			} else {
				taskQuery = taskQuery.taskAssignee(userId); // ??????????????????????????????????????????
			}
		}

		// ????????????????????????????????????or??????
		if (isQueryJobs) {
			taskQuery = taskQuery.endOr();
		}

		List<Task> taskList = new ArrayList<Task>();
		// ????????????key????????????????????????????????????????????????????????????????????????
		if (CheckDataUtil.isNotNull(processBusinessKeyList))
		{
			for(String bKey : processBusinessKeyList)
			{
				taskList.addAll(taskQuery.processInstanceBusinessKey(bKey).list());
			}
		}
		else
		{
			taskQuery = taskQuery.processInstanceBusinessKey(ActivitiConstant.DEF_PROCESS_INST_BUSINESS_KEY); // ????????????key
			taskList = taskQuery.list();
		}

		// ????????????????????????????????????
		List<String> bTypeList = null;
		if (CheckDataUtil.isNotNull(businessTypeList))
		{
			bTypeList = new ArrayList<String>();
			for(String businessType : businessTypeList)
			{
				bTypeList.add(ActivitiConstant.PROCESS_KEY_SPAN + businessType + ActivitiConstant.PROCESS_KEY_SPAN); // ????????????????????????
			}
		}

		// Map<????????????Key, Map<??????????????????ID, TasksInfo>>
		Map<String, Map<String, TasksInfo>> tasksInfoMaps = new HashMap<String, Map<String, TasksInfo>>();
		// ????????????ID????????????Key???????????????????????????
		Map<String, String> processKeys = new HashMap<String, String>();
		for (Task task : taskList) {
			// ??????????????????????????????
			if(bTypeList != null)
			{
				boolean isTrueType = false;
				for(String bType : bTypeList)
				{
					if(task.getProcessDefinitionId().indexOf(bType) > -1)
					{
						isTrueType = true;
						break;
					}
				}
				if(!isTrueType)
				{
					continue;
				}
			}
			String processKey = null;
			if (processKeys.containsKey(task.getProcessDefinitionId())) {
				processKey = processKeys.get(task.getProcessDefinitionId());
			} else {
				// ??????key
				processKey = getProcessKeyById(task.getProcessDefinitionId());
				processKeys.put(task.getProcessDefinitionId(), processKey);
			}
			Map<String, TasksInfo> tasksInfoMap = null;
			if (!tasksInfoMaps.containsKey(processKey)) {
				// ??????map??????????????????????????????
				tasksInfoMap = new HashMap<String, TasksInfo>();
				tasksInfoMaps.put(processKey, tasksInfoMap);
				TasksInfo tasksInfo = createTasksInfo(task, processKey);
				tasksInfoMap.put(task.getTaskDefinitionKey(), tasksInfo);
			} else {
				tasksInfoMap = tasksInfoMaps.get(processKey);
				if (!tasksInfoMap.containsKey(task.getTaskDefinitionKey())) {
					// ????????????????????????????????????????????????
					TasksInfo tasksInfo = createTasksInfo(task, processKey);
					tasksInfoMap.put(task.getTaskDefinitionKey(), tasksInfo);
				} else {
					TasksInfo tasksInfo = tasksInfoMap.get(task.getTaskDefinitionKey());
					tasksInfo.setCount(tasksInfo.getCount() + 1);
				}
			}
		}
		return tasksInfoMaps;
	}

	@Override
	public String getProcessPreview(String processId) throws WorkFlowException, IOException {
		CheckDataUtil.checkNull(processId, "processId");
		InputStream inputStream = null;
		try {
			inputStream = repositoryService.getProcessDiagram(processId);
		} catch (ActivitiObjectNotFoundException e) {
			throw new WorkFlowException("60501", "?????????????????????");
		}

		if (inputStream == null) {
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
	public ProcessInstData getProcessInstData(String processInstanceId) throws WorkFlowException {
		CheckDataUtil.checkNull(processInstanceId, "processInstanceId");
		HistoricProcessInstanceQuery historicProcessInstanceQuery = historyService.createHistoricProcessInstanceQuery();
		historicProcessInstanceQuery = historicProcessInstanceQuery.processInstanceId(processInstanceId);
		HistoricProcessInstance historicProcessInstance = historicProcessInstanceQuery.singleResult();
		ProcessInstData data = makeProcessInstanceData(historicProcessInstance);
		return data;
	}

	@Override
	public List<TaskSampleData> getProcessTasks(String processInstanceId) throws WorkFlowException {
		CheckDataUtil.checkNull(processInstanceId, "processInstanceId");
		HistoricTaskInstanceQuery historicTaskInstanceQuery = historyService.createHistoricTaskInstanceQuery();
		List<HistoricTaskInstance> list = historicTaskInstanceQuery.processInstanceId(processInstanceId).orderByTaskCreateTime().asc().list();
		List<TaskSampleData> tasklist = new ArrayList<TaskSampleData>();
		for(HistoricTaskInstance task : list)
		{
			tasklist.add(makeTaskSampleData(task));
		}
		return tasklist;
	}

	/**
	 * @Title: makeTaskData
	 * @Description: ?????????TaskData
	 * @param task
	 * @return ????????????
	 * @return TaskData ????????????
	 *
	 */
	@SuppressWarnings("unchecked")
	private TaskData makeTaskData(Task task, int stateType) {
		if (task == null)
			return null;
		TaskData taskData = new TaskData();
		taskData.setId(task.getId());
		taskData.setTaskDefId(task.getTaskDefinitionKey());
		taskData.setAssigness(task.getAssignee());
		taskData.setCreateTime(task.getCreateTime());
		taskData.setExecutionId(task.getExecutionId());
		taskData.setName(task.getName());
		taskData.setProcessDefinitionId(task.getProcessDefinitionId());
		taskData.setProcessInstanceId(task.getProcessInstanceId());
		taskData.setProcessDefinitionName(getProcessDefName(task.getProcessDefinitionId()));
		taskData.setDueDate(task.getDueDate());
		taskData.setStateType(stateType);
		taskData.setProcessInstBusinessKey(getProcessInstBusinessKey(task.getProcessInstanceId()));

		// ??????????????????????????????????????????URL??????
		List<FormData> formDataList = new ArrayList<FormData>();
		List<ProcessProperty> propertyList = new ArrayList<ProcessProperty>();
		List<SignUsersData> signUsersList = new ArrayList<SignUsersData>();
		List<ClaimUsersData> claimUsersList = new ArrayList<ClaimUsersData>();
		List<ClaimGroupData> claimGroupList = new ArrayList<ClaimGroupData>();
		List<JudgeProperty> judgeList = new ArrayList<JudgeProperty>();
		TaskFormData fdata = formService.getTaskFormData(task.getId());
		List<FormProperty> fvalues = fdata.getFormProperties();
		for (FormProperty fvalue : fvalues) {
			String id = fvalue.getId();
			String[] propertyTypes = id.split(ActivitiConstant.FORM_PROPERTY_KEY_SPAN);
			String propertyType = propertyTypes[0];

			switch (propertyType) {
				case ActivitiConstant.SMARTFORM_FORM_ID_HEAD: // ??????
				{
					FormData form = new FormData();
					form.setId(propertyTypes[1]);
					form.setTitle(fvalue.getName());
					form.setReadable(fvalue.isReadable());
					form.setWritable(fvalue.isWritable());
					form.setRequired(fvalue.isRequired());
					if (fvalue.getValue() != null) {
						List<FormInstanceData> formInstanceList = JSONObject.parseArray(fvalue.getValue(),
								FormInstanceData.class);
						form.setDataList(formInstanceList);
					}

					formDataList.add(form);
				}
				break;
				case ActivitiConstant.RETRIEVE_KEY: // ????????????
				{
					// ???????????????????????????
				}
				break;
				case ActivitiConstant.SIGN_USERS_FORM_ID_HEAD: // ????????????
				{
					SignUsersData signUsers = new SignUsersData();
					String[] userKeys = fvalue.getValue().split(ActivitiConstant.FORM_PROPERTY_KEY_SPAN);
					String usersKey = null; // ???????????????????????????key
					String defusersKey = null; // ?????????????????????????????????key
					usersKey = userKeys[0];
					if (userKeys.length == 2) {
						defusersKey = userKeys[1];
					}

					signUsers.setId(usersKey);
					signUsers.setName(fvalue.getName());

					// ????????????????????????
					Object users = taskService.getVariable(task.getId(), usersKey);
					// ????????????????????????
					List<UserInfo> userList = new ArrayList<UserInfo>();
					if (users != null) {
						List<String> useridlist = (List<String>) users;
						for (String userid : useridlist) {
							userList.add(loadUserInfo(userid, false));
						}
					}
					signUsers.setUserList(userList);
					if (defusersKey != null) {
						// ??????????????????????????????
						Object defusers = taskService.getVariable(task.getId(), defusersKey);
						// ??????????????????????????????
						List<UserInfo> defUserList = new ArrayList<UserInfo>();
						if (defusers != null) {
							String[] useridlist = defusers.toString().split(",");
							for (String userid : useridlist) {
								defUserList.add(loadUserInfo(userid, false));
							}
						}
						signUsers.setUserDefList(defUserList);
					}
					signUsersList.add(signUsers);
				}
				break;
				case ActivitiConstant.CLAIM_USERS_FORM_ID_HEAD: // ????????????
				{
					ClaimUsersData claimUsers = new ClaimUsersData();
					String[] userKeys = fvalue.getValue().split(ActivitiConstant.FORM_PROPERTY_KEY_SPAN);
					String usersKey = null; // ???????????????????????????key
					String defusersKey = null; // ?????????????????????????????????key
					usersKey = userKeys[0];
					if (userKeys.length == 2) {
						defusersKey = userKeys[1];
					}

					claimUsers.setId(usersKey);
					claimUsers.setName(fvalue.getName());

					// ????????????????????????
					Object users = taskService.getVariable(task.getId(), usersKey);
					// ????????????????????????
					List<UserInfo> userList = new ArrayList<UserInfo>();
					if (users != null) {
						List<String> useridlist = (List<String>) users;
						for (String userid : useridlist) {
							userList.add(loadUserInfo(userid, false));
						}
					}
					claimUsers.setUserList(userList);
					if (defusersKey != null) {
						// ??????????????????????????????
						Object defusers = taskService.getVariable(task.getId(), defusersKey);
						// ??????????????????????????????
						List<UserInfo> defUserList = new ArrayList<UserInfo>();
						if (defusers != null) {
							String[] useridlist = defusers.toString().split(",");
							for (String userid : useridlist) {
								defUserList.add(loadUserInfo(userid, true));
							}
						}
						claimUsers.setUserDefList(defUserList);
					}
					claimUsersList.add(claimUsers);
				}
				break;
				case ActivitiConstant.CLAIM_GROUPS_FORM_ID_HEAD: // ?????????
				{
					// ???????????????????????????
				}
				break;
				case ActivitiConstant.CLAIM_GROUPS_VALUE_FORM_ID_HEAD: // ???????????????
				{
					ClaimGroupData claimGroupData = new ClaimGroupData();
					String[] keys = fvalue.getValue().split(ActivitiConstant.FORM_PROPERTY_KEY_SPAN);
					String groupKey = keys[0]; // ????????????????????????key
					claimGroupData.setId(groupKey);
					claimGroupData.setName(fvalue.getName());
					claimGroupList.add(claimGroupData);
				}
				break;
				case ActivitiConstant.CLAIM_GROUPS_DATA_FORM_ID_HEAD: // ???????????????--?????????
				{
					ClaimGroupData claimGroupData = new ClaimGroupData();
					String[] keys = fvalue.getValue().split(ActivitiConstant.FORM_PROPERTY_KEY_SPAN);
					String groupKey = keys[0]; // ????????????????????????key
					claimGroupData.setId(groupKey);
					claimGroupData.setName(fvalue.getName());
					claimGroupList.add(claimGroupData);
				}
				break;
				case ActivitiConstant.TASK_JUDGE_HEAD: // ???????????????
				{
					// ?????????????????????
					JudgeProperty judge = new JudgeProperty();
					judge.setId(id);
					judge.setName(fvalue.getName());
					judge.setValue(fvalue.getValue());
					judge.setReadable(fvalue.isReadable());
					judge.setWritable(fvalue.isWritable());
					// ???????????????
					Map<String, String> judgeMap = (Map<String, String>) fvalue.getType().getInformation("values");
					if (judgeMap != null) {
						List<JudgeInfo> infoList = new ArrayList<JudgeInfo>();
						Iterator<Entry<String, String>> iter = judgeMap.entrySet().iterator();
						while (iter.hasNext()) {
							Entry<String, String> entry = iter.next();
							JudgeInfo judgeInfo = new JudgeInfo();
							judgeInfo.setValue(entry.getKey());
							judgeInfo.setName(entry.getValue());
							infoList.add(judgeInfo);
						}
						judge.setInfoList(infoList);
					}
					judgeList.add(judge);
				}
				break;
				case ActivitiConstant.BUSINESS_KEY: // ??????url
				{
					taskData.setBusinessValue(fvalue.getValue());
				}
				break;
				case ActivitiConstant.EXPIRED_KEY: // ??????????????????
				{
					taskData.setBusinessExpired(Integer.valueOf(fvalue.getValue()));
				}
				break;
				default: // ???????????????
				{
					ProcessProperty property = new ProcessProperty();
					property.setId(id);
					property.setName(fvalue.getName());
					property.setValue(fvalue.getValue());
					property.setType(fvalue.getType().getName());
					property.setReadable(fvalue.isReadable());
					property.setWritable(fvalue.isWritable());
					property.setRequired(fvalue.isRequired());
					propertyList.add(property);
				}
				break;
			}
		}
		taskData.setClaimGroupList(claimGroupList);
		taskData.setFormDataList(formDataList);
		taskData.setPropertyList(propertyList);
		taskData.setSignUsersList(signUsersList);
		taskData.setClaimUsersList(claimUsersList);
		taskData.setJudgeList(judgeList);

		// ??????????????????
		Map<String, String> exValues = new HashMap<String, String>();
		List<HistoricVariableInstance> values = historyService.createHistoricVariableInstanceQuery()
				.taskId(task.getId()).list();
		if (values != null && !values.isEmpty()) {
			for (HistoricVariableInstance value : values) {
				String id = value.getVariableName();
				String[] propertyTypes = id.split(ActivitiConstant.LOCAL_PROPERTY_KEY_SPAN);
				String propertyType = propertyTypes[0];
				switch (propertyType) {
					case ActivitiConstant.BUSINESS_PROPERTY_KEY: // ?????????????????????
					{
						exValues.put(propertyTypes[1], value.getValue().toString());
					}
					break;
					default: // ????????????????????????
						break;
				}
			}
		}
		taskData.setExValues(exValues);
		return taskData;
	}

	/**
	 * @Title: makeTaskData
	 * @Description: ?????????TaskData ??????????????????
	 * @param task
	 * @return ????????????
	 * @return TaskData ????????????
	 *
	 */
	@SuppressWarnings("unchecked")
	private TaskData makeTaskData(HistoricTaskInstance task) {
		if (task == null)
			return null;
		TaskData taskData = new TaskData();
		taskData.setId(task.getId());
		taskData.setTaskDefId(task.getTaskDefinitionKey());
		taskData.setAssigness(task.getAssignee());
		taskData.setCreateTime(task.getCreateTime());
		taskData.setEndTime(task.getEndTime());
		taskData.setExecutionId(task.getExecutionId());
		taskData.setName(task.getName());
		taskData.setProcessDefinitionId(task.getProcessDefinitionId());
		taskData.setProcessInstanceId(task.getProcessInstanceId());
		taskData.setDeleteReason(task.getDeleteReason());
		taskData.setProcessDefinitionName(getProcessDefName(task.getProcessDefinitionId()));
		taskData.setProcessInstBusinessKey(getProcessInstBusinessKey(task.getProcessInstanceId()));

		List<HistoricVariableInstance> values = historyService.createHistoricVariableInstanceQuery()
				.taskId(task.getId()).list();
		List<FormData> formDataList = new ArrayList<FormData>();
		List<ProcessProperty> propertyList = new ArrayList<ProcessProperty>();
		List<SignUsersData> signUsersList = new ArrayList<SignUsersData>();
		List<ClaimUsersData> claimUsersList = new ArrayList<ClaimUsersData>();
		Map<String, String> exValues = new HashMap<String, String>();
		if (values != null && !values.isEmpty()) {
			for (HistoricVariableInstance value : values) {
				String id = value.getVariableName();
				String[] propertyTypes = id.split(ActivitiConstant.LOCAL_PROPERTY_KEY_SPAN);
				String propertyType = propertyTypes[0];
				switch (propertyType) {
					case ActivitiConstant.BUSINESS_KEY: // ??????URL
					{
						taskData.setBusinessValue(value.getValue().toString());
					}
					break;
					case ActivitiConstant.EXPIRED_KEY: // ??????????????????
					{
						taskData.setBusinessExpired(Integer.valueOf(value.getValue().toString()));
					}
					break;
					case ActivitiConstant.SMARTFORM_FORM_ID_HEAD: // ??????
					{
						FormData form = new FormData();
						form.setId(propertyTypes[1]);
						form.setTitle(propertyTypes[2]);
						form.setReadable(true);
						form.setWritable(false);
						form.setRequired(false);
						if (value.getValue() != null) {
							List<FormInstanceData> formInstanceList = JSONObject.parseArray(value.getValue().toString(),
									FormInstanceData.class);
							form.setDataList(formInstanceList);
						}
						formDataList.add(form);
					}
					break;
					case ActivitiConstant.RETRIEVE_KEY: // ????????????
					{

						String retrieveTaskDefIds = value.getValue().toString()
								.split(ActivitiConstant.RETRIEVE_PROPERTY_VALUE_SPAN)[0];
						taskData.setCanRetrieve(canRetrieveTask(task.getProcessInstanceId(), retrieveTaskDefIds));
					}
					break;
					case ActivitiConstant.SIGN_USERS_FORM_ID_HEAD: // ????????????
					{
						SignUsersData signUsers = new SignUsersData();
						String usersKey = propertyTypes[1]; // ???????????????????????????key

						signUsers.setId(usersKey);
						signUsers.setName(propertyTypes[2]);

						// ????????????????????????
						Object users = value.getValue();
						// ????????????????????????
						List<UserInfo> userList = new ArrayList<UserInfo>();
						if (users != null) {
							List<String> useridlist = (List<String>) users;
							for (String userid : useridlist) {
								userList.add(loadUserInfo(userid, false));
							}
						}
						signUsers.setUserList(userList);
						signUsersList.add(signUsers);
					}
					break;
					case ActivitiConstant.CLAIM_USERS_FORM_ID_HEAD: // ????????????
					{
						ClaimUsersData claimUsers = new ClaimUsersData();
						String usersKey = propertyTypes[1]; // ???????????????????????????key

						claimUsers.setId(usersKey);
						claimUsers.setName(propertyTypes[2]);

						// ????????????????????????
						Object users = value.getValue();
						// ????????????????????????
						List<UserInfo> userList = new ArrayList<UserInfo>();
						if (users != null) {
							List<String> useridlist = (List<String>) users;
							for (String userid : useridlist) {
								userList.add(loadUserInfo(userid, false));
							}
						}
						claimUsers.setUserList(userList);
						claimUsersList.add(claimUsers);

					}
					break;
					case ActivitiConstant.CLAIM_GROUPS_FORM_ID_HEAD: // ?????????
					{
						// ???????????????????????????
					}
					break;
					case ActivitiConstant.BUSINESS_PROPERTY_KEY: // ?????????????????????
					{
						exValues.put(propertyTypes[1], String.valueOf(value.getValue()));
					}
					break;
					default: // ???????????????
					{
						if (propertyTypes.length != 3) {
							break;
						}
						ProcessProperty property = new ProcessProperty();
						property.setId(propertyTypes[1]);
						property.setName(propertyTypes[2]);
						property.setValue(String.valueOf(value.getValue()));
						property.setType(value.getVariableTypeName());
						property.setReadable(true);
						property.setWritable(false);
						property.setRequired(false);
						propertyList.add(property);
					}
					break;
				}
			}
		}
		taskData.setFormDataList(formDataList);
		taskData.setPropertyList(propertyList);
		taskData.setSignUsersList(signUsersList);
		taskData.setClaimUsersList(claimUsersList);
		taskData.setExValues(exValues);
		return taskData;
	}

	/**
	 * @Title: makeTaskSampleData
	 * @Description: ?????????TaskSampleData
	 * @param task
	 * @return ????????????
	 * @return TaskSampleData ????????????
	 *
	 */
	@SuppressWarnings("unchecked")
	private TaskSampleData makeTaskSampleData(Task task, int stateType, boolean hasBusinessKey, Map<String, TaskSampleData> paramsCache) {
		TaskSampleData taskSampleData = new TaskSampleData();
		taskSampleData.setId(task.getId());
		taskSampleData.setTaskDefId(task.getTaskDefinitionKey());
		taskSampleData.setName(task.getName());
		taskSampleData.setCreateTime(task.getCreateTime());
		taskSampleData.setProcessDefinitionId(task.getProcessDefinitionId());
		taskSampleData.setProcessInstanceId(task.getProcessInstanceId());
		taskSampleData.setExecutionId(task.getExecutionId());
		taskSampleData.setDueDate(task.getDueDate());
		taskSampleData.setStateType(stateType);
		taskSampleData.setAssignee(task.getAssignee());
		if(hasBusinessKey)
		{
			taskSampleData.setProcessInstBusinessKey(getProcessInstBusinessKey(task.getProcessInstanceId()));
		}

		// ??????????????????????????????
		if(paramsCache.containsKey(taskSampleData.getTaskDefId()))
		{
			TaskSampleData taskCache = paramsCache.get(taskSampleData.getTaskDefId());
			taskSampleData.setBusinessValue(taskCache.getBusinessValue());
			taskSampleData.setBusinessExpired(Integer.valueOf(taskCache.getBusinessExpired()));
			taskSampleData.setJudgeList(taskCache.getJudgeList());
		}
		else
		{
			// ???????????????????????????????????????
			List<JudgeProperty> judgeList = new ArrayList<JudgeProperty>();
			TaskFormData fdata = formService.getTaskFormData(task.getId());
			List<FormProperty> fvalues = fdata.getFormProperties();
			for (FormProperty fvalue : fvalues) {
				String id = fvalue.getId();
				String[] propertyTypes = id.split(ActivitiConstant.FORM_PROPERTY_KEY_SPAN);
				String propertyType = propertyTypes[0];
				switch (propertyType) {
					case ActivitiConstant.BUSINESS_KEY: // ??????URL
					{
						taskSampleData.setBusinessValue(fvalue.getValue());
					}
					break;
					case ActivitiConstant.EXPIRED_KEY: // ??????????????????
					{
						taskSampleData.setBusinessExpired(Integer.valueOf(fvalue.getValue()));
					}
					break;
					case ActivitiConstant.TASK_JUDGE_HEAD: // ???????????????
					{
						// ?????????????????????
						JudgeProperty judge = new JudgeProperty();
						judge.setId(id);
						judge.setName(fvalue.getName());
						judge.setValue(fvalue.getValue());
						judge.setReadable(fvalue.isReadable());
						judge.setWritable(fvalue.isWritable());
						// ???????????????
						Map<String, String> judgeMap = (Map<String, String>) fvalue.getType().getInformation("values");
						if (judgeMap != null) {
							List<JudgeInfo> infoList = new ArrayList<JudgeInfo>();
							Iterator<Entry<String, String>> iter = judgeMap.entrySet().iterator();
							while (iter.hasNext()) {
								Entry<String, String> entry = iter.next();
								JudgeInfo judgeInfo = new JudgeInfo();
								judgeInfo.setValue(entry.getKey());
								judgeInfo.setName(entry.getValue());
								infoList.add(judgeInfo);
							}
							judge.setInfoList(infoList);
						}
						judgeList.add(judge);
					}
					break;
				}

			}

			taskSampleData.setJudgeList(judgeList);

			// ????????????
			paramsCache.put(taskSampleData.getTaskDefId(), taskSampleData);
		}


		return taskSampleData;
	}

	/**
	 * @Title: makeTaskSampleData
	 * @Description: ?????????TaskSampleData ??????????????????
	 * @param task
	 * @return ????????????
	 * @return TaskSampleData ????????????
	 *
	 */
	private TaskSampleData makeTaskSampleData(HistoricTaskInstance task) {
		TaskSampleData taskSampleData = new TaskSampleData();
		taskSampleData.setId(task.getId());
		taskSampleData.setTaskDefId(task.getTaskDefinitionKey());
		taskSampleData.setName(task.getName());
		taskSampleData.setCreateTime(task.getCreateTime());
		taskSampleData.setEndTime(task.getEndTime());
		taskSampleData.setProcessDefinitionId(task.getProcessDefinitionId());
		taskSampleData.setProcessInstanceId(task.getProcessInstanceId());
		taskSampleData.setExecutionId(task.getExecutionId());
		taskSampleData.setProcessInstBusinessKey(getProcessInstBusinessKey(task.getProcessInstanceId()));
		taskSampleData.setAssignee(task.getAssignee());
		HistoricVariableInstance value = historyService.createHistoricVariableInstanceQuery().taskId(task.getId())
				.variableName(ActivitiConstant.BUSINESS_KEY).singleResult();
		if (value != null) {
			taskSampleData.setBusinessValue(String.valueOf(value.getValue()));
		}

		value = historyService.createHistoricVariableInstanceQuery().taskId(task.getId())
				.variableName(ActivitiConstant.EXPIRED_KEY).singleResult();
		if (value != null) {
			taskSampleData.setBusinessExpired(Integer.valueOf(String.valueOf(value.getValue())));
		}

		value = historyService.createHistoricVariableInstanceQuery().taskId(task.getId())
				.variableName(ActivitiConstant.RETRIEVE_KEY).singleResult();
		if (value != null) {
			String retrieveTaskDefIds = value.getValue().toString()
					.split(ActivitiConstant.RETRIEVE_PROPERTY_VALUE_SPAN)[0];
			taskSampleData.setCanRetrieve(canRetrieveTask(task.getProcessInstanceId(), retrieveTaskDefIds));
		}

		return taskSampleData;
	}

	/**
	 * @Title: getBusinessValue
	 * @Description: ????????????????????????
	 * @param taskId
	 * @return ????????????
	 * @return String ????????????
	 *
	 */
	private String getTaskBusinessValue(String taskId) {
		TaskFormData fdata = formService.getTaskFormData(taskId);
		List<FormProperty> fvalues = fdata.getFormProperties();
		for (FormProperty fvalue : fvalues) {
			String id = fvalue.getId();
			if (id.equals(ActivitiConstant.BUSINESS_KEY)) // ??????URL
			{
				return fvalue.getValue();
			}
		}
		return null;
	}

	/**
	 * @Title: loadUserInfo
	 * @Description: ??????????????????--????????????????????????????????????????????????????????????????????????
	 * @param userId
	 * @return ????????????
	 * @return UserInfo ????????????
	 *
	 */
	private UserInfo loadUserInfo(String userId, boolean loadUnit) {
		UserInfo info = new UserInfo();
		info.setId(userId);

		info.setNick(userId);
		// ????????????????????????????????????
		try{
			List<UserDepartDto> userdeparlist=authInfoUtil.getUserById(userId);
			if(userdeparlist==null || userdeparlist.size()<1){
				info.setNick(userId);
			} else {
				info.setNick(userdeparlist.get(0).getName());
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
			logger.debug(e.getMessage());
		}
		return info;
	}

	/**
	 * @Title: getProcessDefName
	 * @Description: ??????????????????ID????????????????????????
	 * @param processDefId
	 * @return ????????????
	 * @return String ????????????
	 *
	 */
	private String getProcessDefName(String processDefId) {
		ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
				.processDefinitionId(processDefId).singleResult();
		if (processDefinition == null) {
			return null;
		}
		return processDefinition.getName();
	}

	/**
	 * @Title: getProcessInstBusinessKey
	 * @Description: ????????????????????????key
	 * @param processInstId
	 * @return  ????????????
	 * @return String    ????????????
	 *
	 */
	private String getProcessInstBusinessKey(String processInstId) {
		HistoricProcessInstance processInst = historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstId)
				.singleResult();
		if (processInst == null) {
			return null;
		}
		return processInst.getBusinessKey();
	}


	/**
	 * @Title: createTasksInfo
	 * @Description: ?????????????????????
	 * @param task
	 * @return ????????????
	 * @return TasksInfo ????????????
	 *
	 */
	private TasksInfo createTasksInfo(Task task, String processDefinitionKey) {
		TasksInfo tasksInfo = new TasksInfo();
		tasksInfo.setProcessDefinitionKey(processDefinitionKey);
		tasksInfo.setProcessDefinitionName(getProcessDefName(task.getProcessDefinitionId()));
		tasksInfo.setName(task.getName());
		tasksInfo.setTaskDefId(task.getTaskDefinitionKey());
		tasksInfo.setBusinessValue(getTaskBusinessValue(task.getId()));
		tasksInfo.setCount(1);
		tasksInfo.setProcessBusinessType(getProcessTypeByKey(processDefinitionKey));
		return tasksInfo;
	}

	/**
	 * @Title: getProcessKeyById
	 * @Description: ??????????????????ID??????????????????key
	 * @param processDefId
	 * @return ????????????
	 * @return String ????????????
	 *
	 */
	private String getProcessKeyById(String processDefId) {
		return processDefId.substring(0, processDefId.indexOf(":"));
//		ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(processDefId).singleResult();
//		if(processDefinition == null)
//		{
//			return null;
//		}
//		return processDefinition.getKey();
	}

	/**
	 * @Title: getProcessTypeByKey
	 * @Description: ????????????key???????????????????????????????????????key????????? ????????????_??????????????????_key
	 * @param processKey
	 * @return ????????????
	 * @return String ????????????
	 *
	 */
	private String getProcessTypeByKey(String processKey) {
		if (CheckDataUtil.isNull(processKey)) {
			return null;
		}
		String[] values = processKey.split(ActivitiConstant.PROCESS_KEY_SPAN);
		if (values.length != 3) {
			return null;
		}
		return values[1];
	}

	/**
	 * @Title: findTask
	 * @Description: ????????????
	 * @param taskId
	 * @return ????????????
	 * @return Task ????????????
	 *
	 */
	private Task findTask(String taskId) {
		Task task = taskService // ???????????????????????????????????????Service
				.createTaskQuery() // ????????????????????????
				.taskId(taskId) // ????????????????????????
				.singleResult();
		return task;
	}

	/**
	 * @Title: getSubProcessInstanceByParent
	 * @Description: ????????????????????????????????????????????????
	 * @param parentProcessId
	 * @return ????????????
	 * @return List<ProcessInstance> ????????????
	 *
	 */
	private List<ProcessInstance> getSubProcessInstanceByParent(String parentProcessId) {
		return runtimeService.createProcessInstanceQuery().superProcessInstanceId(parentProcessId).list();
	}

	/**
	 * @ClassName: EProcessInstanceState
	 * @Description: ??????????????????
	 * @author KaminanGTO
	 * @date 2018???11???9??? ??????5:39:11
	 *
	 */
	enum EProcessInstanceState {

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

		EProcessInstanceState(int value) {
			this.value = value;
		}

		static EProcessInstanceState valueOf(int value) {
			EProcessInstanceState state = EProcessInstanceState.All;
			if (value > -1 && value < EProcessInstanceState.values().length - 1) {
				state = EProcessInstanceState.values()[value + 1];
			}
			return state;
		}
	}

	/**
	 * @Title: makeProcessInstanceData
	 * @Description: ???????????????????????????-??????????????????
	 * @param historicProcessInstance
	 * @return ????????????
	 * @return ProcessInstData ????????????
	 *
	 */
	private ProcessInstData makeProcessInstanceData(HistoricProcessInstance historicProcessInstance) {
		ProcessInstData data = new ProcessInstData();
		data.setId(historicProcessInstance.getId());
		data.setName(historicProcessInstance.getProcessDefinitionName());
		data.setProcessDefId(historicProcessInstance.getProcessDefinitionId());
		data.setStartTime(historicProcessInstance.getStartTime());
		data.setEndTime(historicProcessInstance.getEndTime());
		data.setDeleteReason(historicProcessInstance.getDeleteReason());
		if (data.getEndTime() == null) {
			// ????????????????????????????????????
			ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
					.processInstanceId(data.getId()).singleResult();
			data.setState(processInstance.isSuspended() ? EProcessInstanceState.Suspend.value
					: EProcessInstanceState.Active.value);
		} else {
			// ????????????????????????????????????
			if (historicProcessInstance.getDeleteReason() != null) {
				data.setState(EProcessInstanceState.Delete.value);
			} else {
				data.setState(EProcessInstanceState.Finish.value);
			}

		}
		return data;
	}

	/**
	 * @Title: saveTaskExValues
	 * @Description: ??????????????????????????????????????????
	 * @param taskId
	 * @param exValues ????????????
	 * @return void ????????????
	 *
	 */
	@Transactional
	public void saveTaskExValues(String taskId, Map<String, String> exValues) {
		Map<String, String> values = new HashMap<String, String>();
		Iterator<Entry<String, String>> iter = exValues.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<String, String> entry = iter.next();
			values.put(
					ActivitiConstant.BUSINESS_PROPERTY_KEY + ActivitiConstant.LOCAL_PROPERTY_KEY_SPAN + entry.getKey(),
					entry.getValue());
		}
		taskService.setVariablesLocal(taskId, values);
	}

	/**
	 * @Title: getUserFinishedKey
	 * @Description: ?????????????????????????????????key
	 * @return ????????????
	 * @return String ????????????
	 *
	 */
	private String getUserFinishedKey() {
		return JedisMgr_wf.KeyHead + ActivitiConstant.REDIS_USER_FINISHED_VIEW_KEY;
	}

	/**
	 * @Title: getUnitFinishedKey
	 * @Description: ?????????????????????????????????key
	 * @return ????????????
	 * @return String ????????????
	 *
	 */
	private String getUnitFinishedKey() {
		return JedisMgr_wf.KeyHead + ActivitiConstant.REDIS_UNIT_FINISHED_VIEW_KEY;
	}

	/**
	 * @Title: getReplaceUsers
	 * @Description: ????????????????????????????????????
	 * @param userId
	 * @return ????????????
	 * @return List<String> ????????????
	 *
	 */
	private List<String> getReplaceUsers(String userId, String processId) {
		UserReplace userReplace = userReplaceDao.get(userId);
		if (userReplace == null) {
			return null;
		}
		List<String> userList = new ArrayList<String>(); // ????????????????????????
		List<String> delKeys = new ArrayList<String>(); // ?????????????????????
		long now = System.currentTimeMillis();
		if (CheckDataUtil.isNotNull(processId)) {
			// ??????????????????????????????
			if (!userReplace.getReplaceInfos().containsKey(processId)) {
				return null;
			}
			List<UserReplaceInfo> UserReplaceList = userReplace.getReplaceInfos().get(processId);
			for (UserReplaceInfo userReplaceInfo : UserReplaceList) {
				if (now > userReplaceInfo.getEndTime().getTime()) {
					// ?????????
					delKeys.add(makeReplaceKey(processId, userReplaceInfo.getReplaceUser()));
				} else if (userReplaceInfo.getStartTime().getTime() > now) {
					// ?????????
					userList.add(userReplaceInfo.getReplaceUser());
				}
			}
		} else {
			// ??????????????????????????????
			Iterator<Entry<String, List<UserReplaceInfo>>> iter = userReplace.getReplaceInfos().entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry<String, List<UserReplaceInfo>> entry = iter.next();
				List<UserReplaceInfo> UserReplaceList = entry.getValue();
				for (UserReplaceInfo userReplaceInfo : UserReplaceList) {
					if (now > userReplaceInfo.getEndTime().getTime()) {
						// ?????????
						delKeys.add(makeReplaceKey(entry.getKey(), userReplaceInfo.getReplaceUser()));
					} else if (userReplaceInfo.getStartTime().getTime() > now) {
						// ?????????
						userList.add(userReplaceInfo.getReplaceUser());
					}
				}
			}
		}
		// ?????????????????????????????????
		if (!delKeys.isEmpty()) {
			userReplaceDao.deleteParams(userId, delKeys);
		}
		return userList.isEmpty() ? null : userList;
	}

	/**
	 * @Title: makeReplaceKey
	 * @Description: ??????????????????hashkey
	 * @param processId
	 * @param userId
	 * @return ????????????
	 * @return String ????????????
	 *
	 */
	private String makeReplaceKey(String processId, String userId) {
		return processId + ActivitiConstant.REDIS_HASHKEY_SPAN + userId;
	}

	/**
	 * @Title: canRetrieveTask
	 * @Description: ????????????????????????
	 * @param processInstanceId
	 * @param taskDefIds
	 * @return ????????????
	 * @return Boolean ????????????
	 *
	 */
	private Boolean canRetrieveTask(String processInstanceId, String taskDefIds) {
		String[] taskDefIdList = taskDefIds.split(ActivitiConstant.RETRIEVE_TASK_SPAN);
		TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(processInstanceId);
		for (String taskDefId : taskDefIdList) {
			if (taskQuery.taskDefinitionKey(taskDefId).count() == 1) {
				return true;
			}
		}
		return false;
	}

	//????????????
	@Transactional
	public void jump(Task startTask, String targetTaskDefId) throws WorkFlowException{
		//??????????????????
		org.activiti.bpmn.model.Process process = repositoryService.getBpmnModel(startTask.getProcessDefinitionId()).getMainProcess();
		//????????????????????????
		FlowNode targetNode = (FlowNode)process.getFlowElement(targetTaskDefId);
		if(targetNode == null)
		{
			throw new WorkFlowException("???????????????????????????. taskDefId:" + targetTaskDefId);
		}
		//????????????????????????
		String executionEntityId = managementService.executeCommand(new DeleteTaskCmd(startTask.getId()));
		if("???????????????????????????".equals(executionEntityId))
		{
			throw new WorkFlowException(executionEntityId);
		}
		//???????????????????????????
		managementService.executeCommand(new SetFLowNodeAndGoCmd(targetNode, executionEntityId));
	}

	//????????????????????????????????????????????????????????????????????????id
	//???????????????NeedsActiveTaskCmd??????????????????????????????????????????????????????????????????????????????????????????Command??????
	public class DeleteTaskCmd extends NeedsActiveTaskCmd<String> {
		private static final long serialVersionUID = 1L;
		public DeleteTaskCmd(String taskId){
			super(taskId);
		}
		public String execute(CommandContext commandContext, TaskEntity currentTask){
			//??????????????????
			TaskEntityManagerImpl taskEntityManager = (TaskEntityManagerImpl)commandContext.getTaskEntityManager();
			//??????????????????????????????????????????????????????
			ExecutionEntity executionEntity = currentTask.getExecution();
			//??????????????????,????????????
			taskEntityManager.deleteTask(currentTask, "??????????????????", false, false);
			return executionEntity.getId();
		}
		public String getSuspendedTaskException() {
			return "???????????????????????????";
		}
	}

	//?????????????????????????????????id?????????????????????
	public class SetFLowNodeAndGoCmd implements Command<Void> {
		private FlowNode flowElement;
		private String executionId;
		public SetFLowNodeAndGoCmd(FlowNode flowElement,String executionId){
			this.flowElement = flowElement;
			this.executionId = executionId;
		}

		public Void execute(CommandContext commandContext){
			//?????????????????????????????????
			List<SequenceFlow> flows = flowElement.getIncomingFlows();
			if(flows==null || flows.size()<1){
				throw new ActivitiException("?????????????????????????????????????????????");
			}
			//?????????????????????????????????????????????????????????????????????????????????????????????????????????
			ExecutionEntity executionEntity = commandContext.getExecutionEntityManager().findById(executionId);
			executionEntity.setCurrentFlowElement(flows.get(0));
			commandContext.getAgenda().planTakeOutgoingSequenceFlowsOperation(executionEntity, true);
			return null;
		}
	}
}
