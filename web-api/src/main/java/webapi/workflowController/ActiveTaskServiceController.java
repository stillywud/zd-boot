package webapi.workflowController;

import workflow.business.model.ActiveTask;
import workflow.business.model.TaskContentData;
import workflow.business.model.TaskData;
import workflow.business.model.TotalTasks;
import workflow.business.service.ActiveTaskService;
import com.baomidou.mybatisplus.core.conditions.query.Query;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import workflow.olddata.model.PageInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * 
 */
//@Service("activeTaskService")
@Controller
@RestController
@RequestMapping(value = "/workflow/activeTask")
public class ActiveTaskServiceController {
	@Autowired
	private ActiveTaskService activeTaskService;

	/**
	 * 分页查询
	 *
	 * @param params
	 * @param page
	 * @return
	 */
	@ResponseBody
	@ApiOperation("分页查询")
	@RequestMapping(value = "/pageList")
	Page<ActiveTask> pageList(ActiveTask params, PageInput page) {
		return activeTaskService.pageList(params, page);
	}

	/**
	 * 根据查询条件查询所有待办任务
	 *
	 * @param params 查询条件
	 * @return
	 */
	@ResponseBody
	@ApiOperation("根据查询条件查询所有待办任务")
	@RequestMapping(value = "/listAll")
	List<ActiveTask> listAll(ActiveTask params) {
		return activeTaskService.listAll(params);
	}

	/**
	 * 根据id查询
	 *
	 * @param id
	 * @return
	 */
	@ResponseBody
	@ApiOperation("根据id查询")
	@RequestMapping(value = "/getActiveTaskById")
	ActiveTask getActiveTaskById(String id) {
		return activeTaskService.getActiveTaskById(id);
	}

	/**
	 * 保存待办任务
	 *
	 * @param processName          流程名称
	 * @param initiator            发起人
	 * @param previousOpResultDesc 上一个任务节点的操作结果
	 * @param list                 工作流返回的待办任务信息
	 * @param data                 待办任务其他信息
	 * @return
	 */
	@ResponseBody
	@ApiOperation("保存待办任务")
	@RequestMapping(value = "/batchSave")
	int batchSave(String processName, String initiator, String previousOpResultDesc, List<TaskData> list, TaskContentData data) {
		return activeTaskService.batchSave(processName, initiator, previousOpResultDesc, list, data);
	}

	/**
	 * 保存待办任务
	 *
	 * @param processName          流程名称
	 * @param initiator            发起人
	 * @param previousOpResultDesc 上一个任务节点的操作结果
	 * @param list                 工作流返回的待办任务信息
	 * @param data                 待办任务其他信息
	 * @return
	 */
	@ResponseBody
	@ApiOperation("保存待办任务")
	@RequestMapping(value = "/batchSave1")
	int batchSave(String processName, String initiator, String previousOpResultDesc, List<TaskData> list, TaskContentData data, String task_batch) {
		return activeTaskService.batchSave(processName, initiator, previousOpResultDesc, list, data, task_batch);
	}

	/**
     * 保存待办任务
     * @param processName 流程名称
     * @param initiator 发起人
     * @param previousOpResultDesc 上一个任务节点的操作结果
     * @param taskData 工作流返回的待办任务信息
     * @param data 待办任务其他信息
     * @return
     */
	@ResponseBody
	@ApiOperation("保存待办任务")
	@RequestMapping(value = "/batchSave2")
	int batchSave(String processName, String initiator, String previousOpResultDesc, TaskData taskData, TaskContentData data){
		return activeTaskService.batchSave(processName, initiator, previousOpResultDesc, taskData, data);
	}
	/**
	 * 保存待办任务
	 * @param processName 流程名称
	 * @param initiator 发起人
	 * @param previousOpResultDesc 上一个任务节点的操作结果
	 * @param taskData 工作流返回的待办任务信息
	 * @param data 待办任务其他信息
	 * @return
	 */
	@ResponseBody
	@ApiOperation("保存待办任务")
	@RequestMapping(value = "/batchSave3")
	int batchSave(String processName, String initiator, String previousOpResultDesc, TaskData taskData,
				  TaskContentData data, String task_batch) {
		return activeTaskService.batchSave(processName, initiator, previousOpResultDesc, taskData, data, task_batch);
	}
	/**
	 * 根据任务id查询待办任务列表
	 * @param taskId
	 * @param exceptActiveTaskId 有值时表示查询除此id外的taskId列表 
	 * @return
	 */
	@ResponseBody
	@ApiOperation("保存待办任务")
	@RequestMapping(value = "/listActiveTask")
	List<ActiveTask> listActiveTask(String taskId, String exceptActiveTaskId){
		return activeTaskService.listActiveTask(taskId, exceptActiveTaskId);
	}

	/**
	 * 根据业务数据id查询待办任务列表
	 * @param contentId
	 * @return
	 */
	@ResponseBody
	@ApiOperation("根据业务数据id查询待办任务列表")
	@RequestMapping(value = "/listActiveTaskByContentId")
	List<ActiveTask> listActiveTaskByContentId(String contentId){
		return activeTaskService.listActiveTaskByContentId(contentId);
	}

	/**
	 * 根据业务数据id查询待办任务列表
	 *
	 * @param taskbatch
	 * @return
	 */
	@ResponseBody
	@ApiOperation("根据task_batch查询待办任务列表")
	@RequestMapping(value = "/listActiveTaskBytaskbatch")
	List<ActiveTask> listActiveTaskBytaskbatch(String taskbatch) {
		return activeTaskService.listActiveTaskBytaskbatch(taskbatch);
	}

	/**
	 * 删除用户userId的某个待办任务
	 * 删除taskId的所有任务
	 *
	 * @param userId
	 * @param taskId
	 * @return
	 */
	@ResponseBody
	@ApiOperation("删除用户userId的某个待办任务")
	@RequestMapping(value = "/delete")
	int delete(String userId, String taskId) {
		return activeTaskService.delete(userId, taskId);
	}

	/**
	 * 根据id删除待办任务
	 * @param id
	 * @return
	 */
	@ResponseBody
	@ApiOperation("根据id删除待办任务")
	@RequestMapping(value = "/deleteById")
	int deleteById(String id){
		return activeTaskService.deleteById(id);
	}
	
	/**
	 * 将工作流任务对象转换成待办任务对象
	 * @param taskData
	 * @return
	 */
	@ResponseBody
	@ApiOperation("将工作流任务对象转换成待办任务对象")
	@RequestMapping(value = "/taskDataToActiveTask")
	ActiveTask taskDataToActiveTask(TaskData taskData){
		return activeTaskService.taskDataToActiveTask(taskData);
	}
	
	/**
     * 获取待办任务总览
     * @param query  查询条件
     * @return
     */
	@ResponseBody
	@ApiOperation("获取待办任务总览")
	@RequestMapping(value = "/getTotalTasks")
    List<TotalTasks> getTotalTasks(Query query){
		return activeTaskService.getTotalTasks(query);
	}
    
    /**
     * 进行暂停、停止、恢复操作时，更新对应状态
     * @param processDefinitionId  流程定义id
     * @param processInstanceId   流程实例id
     * @param processTaskId       流程任务id
     * @param status 状态 0正常1暂停2终止
     * @return
     */
	@ResponseBody
	@ApiOperation("进行暂停、停止、恢复操作时，更新对应状态")
	@RequestMapping(value = "/updateStatus")
    int updateStatus(String processDefinitionId, String processInstanceId, String processTaskId, int status){
		return activeTaskService.updateStatus(processDefinitionId, processInstanceId, processTaskId, status);
	}

    /**
     * 更新数据
     * @param item
     * @return
     */
	@ResponseBody
	@ApiOperation("更新数据")
	@RequestMapping(value = "/updateActiveTask")
    int updateActiveTask(ActiveTask item){
		return activeTaskService.updateActiveTask(item);
	}


}
