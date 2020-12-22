package auth.domain.relation.permission.depart.service;

import auth.entity.DepartPermission;
import com.baomidou.mybatisplus.extension.service.IService;
import auth.entity.PermissionDataRule;

import java.util.List;

/**
 * @Description: 部门权限表
 * @Author: jeecg-boot
 * @Date:   2020-02-11
 * @Version: V1.0
 */
public interface ISysDepartPermissionService extends IService<DepartPermission> {
    /**
     * 保存授权 将上次的权限和这次作比较 差异处理提高效率
     * @param departId
     * @param permissionIds
     * @param lastPermissionIds
     */
    public void saveDepartPermission(String departId,String permissionIds,String lastPermissionIds);

    /**
     * 根据部门id，菜单id获取数据规则
     * @param permissionId
     * @return
     */
    List<PermissionDataRule> getPermRuleListByDeptIdAndPermId(String departId, String permissionId);
}
