package webapi.authController;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import auth.discard.model.SysPermissionTree;
import auth.discard.model.TreeModel;
import auth.discard.util.PermissionDataUtil;
import auth.domain.permission.service.ISysPermissionService;
import auth.domain.relation.permission.depart.service.ISysDepartPermissionService;
import auth.domain.relation.permission.role.service.ISysRolePermissionService;
import auth.domain.relation.permission.rule.serivce.ISysPermissionDataRuleService;
import auth.entity.DepartPermission;
import auth.entity.Permission;
import auth.entity.PermissionDataRule;
import auth.entity.RolePermission;
import commons.api.vo.Result;
import commons.constant.CommonConstant;
import commons.auth.utils.JwtUtil;
import commons.util.MD5Util;
import commons.util.oConvertUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单权限 前端控制器
 */
@Slf4j
@RestController
@RequestMapping("/sys/permission")
public class SysPermissionController {

    private final ISysPermissionService sysPermissionService;

    private final ISysRolePermissionService sysRolePermissionService;

    private final ISysPermissionDataRuleService sysPermissionDataRuleService;

    private final ISysDepartPermissionService sysDepartPermissionService;

    @Autowired
    public SysPermissionController(ISysPermissionService sysPermissionService, ISysRolePermissionService sysRolePermissionService, ISysPermissionDataRuleService sysPermissionDataRuleService, ISysDepartPermissionService sysDepartPermissionService) {
        this.sysPermissionService = sysPermissionService;
        this.sysRolePermissionService = sysRolePermissionService;
        this.sysPermissionDataRuleService = sysPermissionDataRuleService;
        this.sysDepartPermissionService = sysDepartPermissionService;
    }

    /**
     * 加载数据节点
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public Result<List<SysPermissionTree>> list() {
        long start = System.currentTimeMillis();
        Result<List<SysPermissionTree>> result = new Result<>();
        try {
            LambdaQueryWrapper<Permission> query = new LambdaQueryWrapper<>();
            query.eq(Permission::getDelFlag, CommonConstant.DEL_FLAG_0);
            query.orderByAsc(Permission::getSortNo);
            List<Permission> list = sysPermissionService.list(query);
            List<SysPermissionTree> treeList = new ArrayList<>();
            getTreeList(treeList, list, null);
            result.setResult(treeList);
            result.setSuccess(true);
            log.info("======获取全部菜单数据=====耗时:" + (System.currentTimeMillis() - start) + "毫秒");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return result;
    }

    /*update_begin author:wuxianquan date:20190908 for:先查询一级菜单，当用户点击展开菜单时加载子菜单 */

    /**
     * 系统菜单列表(一级菜单)
     */
    @RequestMapping(value = "/getSystemMenuList", method = RequestMethod.GET)
    public Result<List<SysPermissionTree>> getSystemMenuList() {
        long start = System.currentTimeMillis();
        Result<List<SysPermissionTree>> result = new Result<>();
        try {
            LambdaQueryWrapper<Permission> query = new LambdaQueryWrapper<>();
            query.eq(Permission::getMenuType, CommonConstant.MENU_TYPE_0);
            query.eq(Permission::getDelFlag, CommonConstant.DEL_FLAG_0);
            query.orderByAsc(Permission::getSortNo);
            List<Permission> list = sysPermissionService.list(query);
            List<SysPermissionTree> sysPermissionTreeList = new ArrayList<>();
            for (Permission permission : list) {
                SysPermissionTree sysPermissionTree = new SysPermissionTree(permission);
                sysPermissionTreeList.add(sysPermissionTree);
            }
            result.setResult(sysPermissionTreeList);
            result.setSuccess(true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        log.info("======获取一级菜单数据=====耗时:" + (System.currentTimeMillis() - start) + "毫秒");
        return result;
    }

    /**
     * 查询子菜单
     */
    @RequestMapping(value = "/getSystemSubmenu", method = RequestMethod.GET)
    public Result<List<SysPermissionTree>> getSystemSubmenu(@RequestParam("parentId") String parentId) {
        Result<List<SysPermissionTree>> result = new Result<>();
        try {
            LambdaQueryWrapper<Permission> query = new LambdaQueryWrapper<>();
            query.eq(Permission::getParentId, parentId);
            query.eq(Permission::getDelFlag, CommonConstant.DEL_FLAG_0);
            query.orderByAsc(Permission::getSortNo);
            List<Permission> list = sysPermissionService.list(query);
            List<SysPermissionTree> sysPermissionTreeList = new ArrayList<>();
            for (Permission permission : list) {
                SysPermissionTree sysPermissionTree = new SysPermissionTree(permission);
                sysPermissionTreeList.add(sysPermissionTree);
            }
            result.setResult(sysPermissionTreeList);
            result.setSuccess(true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return result;
    }
    /*update_end author:wuxianquan date:20190908 for:先查询一级菜单，当用户点击展开菜单时加载子菜单 */

    // update_begin author:sunjianlei date:20200108 for: 新增批量根据父ID查询子级菜单的接口 -------------

    /**
     * 查询子菜单
     *
     * @param parentIds 父ID（多个采用半角逗号分割）
     * @return 返回 key-value 的 Map
     */
    @GetMapping("/getSystemSubmenuBatch")
    public Result<?> getSystemSubmenuBatch(@RequestParam("parentIds") String parentIds) {
        try {
            LambdaQueryWrapper<Permission> query = new LambdaQueryWrapper<>();
            List<String> parentIdList = Arrays.asList(parentIds.split(","));
            query.in(Permission::getParentId, parentIdList);
            query.eq(Permission::getDelFlag, CommonConstant.DEL_FLAG_0);
            query.orderByAsc(Permission::getSortNo);
            List<Permission> list = sysPermissionService.list(query);
            Map<String, List<SysPermissionTree>> listMap = new HashMap<>();
            for (Permission item : list) {
                String pid = item.getParentId();
                if (parentIdList.contains(pid)) {
                    List<SysPermissionTree> mapList = listMap.get(pid);
                    if (mapList == null) {
                        mapList = new ArrayList<>();
                    }
                    mapList.add(new SysPermissionTree(item));
                    listMap.put(pid, mapList);
                }
            }
            return Result.ok(listMap);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Result.error("批量查询子菜单失败：" + e.getMessage());
        }
    }
    // update_end author:sunjianlei date:20200108 for: 新增批量根据父ID查询子级菜单的接口 -------------

//	/**
//	 * 查询用户拥有的菜单权限和按钮权限（根据用户账号）
//	 *
//	 * @return
//	 */
//	@RequestMapping(value = "/queryByUser", method = RequestMethod.GET)
//	public Result<JSONArray> queryByUser(HttpServletRequest req) {
//		Result<JSONArray> result = new Result<>();
//		try {
//			String username = req.getParameter("username");
//			List<SysPermission> metaList = sysPermissionService.queryByUser(username);
//			JSONArray jsonArray = new JSONArray();
//			this.getPermissionJsonArray(jsonArray, metaList, null);
//			result.setResult(jsonArray);
//			result.success("查询成功");
//		} catch (Exception EModel) {
//			result.error500("查询失败:" + EModel.getMessage());
//			log.error(EModel.getMessage(), EModel);
//		}
//		return result;
//	}

    /**
     * 查询用户拥有的菜单权限和按钮权限（根据TOKEN）
     */
    @RequestMapping(value = "/getUserPermissionByToken", method = RequestMethod.GET)
    public Result<?> getUserPermissionByToken(@RequestParam(name = "token") String token) {
        Result<JSONObject> result = new Result<>();
        try {
            if (oConvertUtils.isEmpty(token)) {
                return Result.error("TOKEN不允许为空！");
            }
            log.info(" ------ 通过令牌获取用户拥有的访问菜单 ---- TOKEN ------ " + token);
            String username = JwtUtil.getUsername(token);
            List<Permission> metaList = sysPermissionService.queryByUser(username);
            //添加首页路由
            //update-begin-author:taoyan date:20200211 for: TASK #3368 【路由缓存】首页的缓存设置有问题，需要根据后台的路由配置来实现是否缓存
            if (!PermissionDataUtil.hasIndexPage(metaList)) {
                Permission indexMenu = sysPermissionService.list(new LambdaQueryWrapper<Permission>().eq(Permission::getName, "首页")).get(0);
                metaList.add(0, indexMenu);
            }
            //update-end-author:taoyan date:20200211 for: TASK #3368 【路由缓存】首页的缓存设置有问题，需要根据后台的路由配置来实现是否缓存
            JSONObject json = new JSONObject();
            JSONArray menujsonArray = new JSONArray();
            this.getPermissionJsonArray(menujsonArray, metaList, null);
            JSONArray authjsonArray = new JSONArray();
            this.getAuthJsonArray(authjsonArray, metaList);
            //查询所有的权限
            LambdaQueryWrapper<Permission> query = new LambdaQueryWrapper<>();
            query.eq(Permission::getDelFlag, CommonConstant.DEL_FLAG_0);
            query.eq(Permission::getMenuType, CommonConstant.MENU_TYPE_2);
            //query.eq(SysPermission::getStatus, "1");
            List<Permission> allAuthList = sysPermissionService.list(query);
            JSONArray allauthjsonArray = new JSONArray();
            this.getAllAuthJsonArray(allauthjsonArray, allAuthList);
            //路由菜单
            json.put("menu", menujsonArray);
            //按钮权限（用户拥有的权限集合）
            json.put("auth", authjsonArray);
            //全部权限配置集合（按钮权限，访问权限）
            json.put("allAuth", allauthjsonArray);
            result.setResult(json);
            result.success("查询成功");
        } catch (Exception e) {
            result.error500("查询失败:" + e.getMessage());
            log.error(e.getMessage(), e);
        }
        return result;
    }

    /**
     * 添加菜单
     */
    @RequiresRoles({"admin"})
    @RequestMapping(value = "/add", method = RequestMethod.POST)
    public Result<Permission> add(@RequestBody Permission permission) {
        Result<Permission> result = new Result<>();
        try {
            permission = PermissionDataUtil.intelligentProcessData(permission);
            sysPermissionService.addPermission(permission);
            result.success("添加成功！");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.error500("操作失败");
        }
        return result;
    }

    /**
     * 编辑菜单
     */
    @RequiresRoles({"admin"})
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<Permission> edit(@RequestBody Permission permission) {
        Result<Permission> result = new Result<>();
        try {
            permission = PermissionDataUtil.intelligentProcessData(permission);
            sysPermissionService.editPermission(permission);
            result.success("修改成功！");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.error500("操作失败");
        }
        return result;
    }

    /**
     * 删除菜单
     */
    @RequiresRoles({"admin"})
    @RequestMapping(value = "/delete", method = RequestMethod.DELETE)
    public Result<Permission> delete(@RequestParam(name = "id") String id) {
        Result<Permission> result = new Result<>();
        try {
            sysPermissionService.deletePermission(id);
            result.success("删除成功!");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.error500(e.getMessage());
        }
        return result;
    }

    /**
     * 批量删除菜单
     */
    @RequiresRoles({"admin"})
    @RequestMapping(value = "/deleteBatch", method = RequestMethod.DELETE)
    public Result<Permission> deleteBatch(@RequestParam(name = "ids") String ids) {
        Result<Permission> result = new Result<>();
        try {
            String[] arr = ids.split(",");
            for (String id : arr) {
                if (oConvertUtils.isNotEmpty(id)) {
                    sysPermissionService.deletePermission(id);
                }
            }
            result.success("删除成功!");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.error500("删除成功!");
        }
        return result;
    }

    /**
     * 获取全部的权限树
     */
    @RequestMapping(value = "/queryTreeList", method = RequestMethod.GET)
    public Result<Map<String, Object>> queryTreeList() {
        Result<Map<String, Object>> result = new Result<>();
        // 全部权限ids
        List<String> ids = new ArrayList<>();
        try {
            LambdaQueryWrapper<Permission> query = new LambdaQueryWrapper<>();
            query.eq(Permission::getDelFlag, CommonConstant.DEL_FLAG_0);
            query.orderByAsc(Permission::getSortNo);
            List<Permission> list = sysPermissionService.list(query);
            for (Permission sysPer : list) {
                ids.add(sysPer.getId());
            }
            List<TreeModel> treeList = new ArrayList<>();
            getTreeModelList(treeList, list, null);

            Map<String, Object> resMap = new HashMap<>();
            resMap.put("treeList", treeList); // 全部树节点数据
            resMap.put("ids", ids);// 全部树ids
            result.setResult(resMap);
            result.setSuccess(true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return result;
    }

    /**
     * 异步加载数据节点
     */
    @RequestMapping(value = "/queryListAsync", method = RequestMethod.GET)
    public Result<List<TreeModel>> queryAsync(@RequestParam(name = "pid", required = false) String parentId) {
        Result<List<TreeModel>> result = new Result<>();
        try {
            List<TreeModel> list = sysPermissionService.queryListByParentId(parentId);
            if (list == null || list.size() <= 0) {
                result.error500("未找到角色信息");
            } else {
                result.setResult(list);
                result.setSuccess(true);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return result;
    }

    /**
     * 查询角色授权
     */
    @RequestMapping(value = "/queryRolePermission", method = RequestMethod.GET)
    public Result<List<String>> queryRolePermission(@RequestParam(name = "roleId") String roleId) {
        Result<List<String>> result = new Result<>();
        try {
            List<RolePermission> list = sysRolePermissionService.list(new QueryWrapper<RolePermission>().lambda().eq(RolePermission::getRoleId, roleId));
            result.setResult(list.stream().map(SysRolePermission -> String.valueOf(SysRolePermission.getPermissionId())).collect(Collectors.toList()));
            result.setSuccess(true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return result;
    }

    /**
     * 保存角色授权
     */
    @RequestMapping(value = "/saveRolePermission", method = RequestMethod.POST)
    @RequiresRoles({"admin"})
    public Result<String> saveRolePermission(@RequestBody JSONObject json) {
        long start = System.currentTimeMillis();
        Result<String> result = new Result<>();
        try {
            String roleId = json.getString("roleId");
            String permissionIds = json.getString("permissionIds");
            String lastPermissionIds = json.getString("lastpermissionIds");
            this.sysRolePermissionService.saveRolePermission(roleId, permissionIds, lastPermissionIds);
            result.success("保存成功！");
            log.info("======角色授权成功=====耗时:" + (System.currentTimeMillis() - start) + "毫秒");
        } catch (Exception e) {
            result.error500("授权失败！");
            log.error(e.getMessage(), e);
        }
        return result;
    }

    private void getTreeList(List<SysPermissionTree> treeList, List<Permission> metaList, SysPermissionTree temp) {
        for (Permission permission : metaList) {
            String tempPid = permission.getParentId();
            SysPermissionTree tree = new SysPermissionTree(permission);
            if (temp == null && oConvertUtils.isEmpty(tempPid)) {
                treeList.add(tree);
                if (!tree.getIsLeaf()) {
                    getTreeList(treeList, metaList, tree);
                }
            } else if (temp != null && tempPid != null && tempPid.equals(temp.getId())) {
                temp.getChildren().add(tree);
                if (!tree.getIsLeaf()) {
                    getTreeList(treeList, metaList, tree);
                }
            }

        }
    }

    private void getTreeModelList(List<TreeModel> treeList, List<Permission> metaList, TreeModel temp) {
        for (Permission permission : metaList) {
            String tempPid = permission.getParentId();
            TreeModel tree = new TreeModel(permission);
            if (temp == null && oConvertUtils.isEmpty(tempPid)) {
                treeList.add(tree);
                if (!tree.getIsLeaf()) {
                    getTreeModelList(treeList, metaList, tree);
                }
            } else if (temp != null && tempPid != null && tempPid.equals(temp.getKey())) {
                temp.getChildren().add(tree);
                if (!tree.getIsLeaf()) {
                    getTreeModelList(treeList, metaList, tree);
                }
            }

        }
    }

    /**
     * 获取权限JSON数组
     */
    private void getAllAuthJsonArray(JSONArray jsonArray, List<Permission> allList) {
        JSONObject json;
        for (Permission permission : allList) {
            json = new JSONObject();
            json.put("action", permission.getPerms());
            json.put("status", permission.getStatus());
            json.put("type", permission.getPermsType());
            json.put("describe", permission.getName());
            jsonArray.add(json);
        }
    }

    /**
     * 获取权限JSON数组
     */
    private void getAuthJsonArray(JSONArray jsonArray, List<Permission> metaList) {
        for (Permission permission : metaList) {
            if (permission.getMenuType() == null) {
                continue;
            }
            JSONObject json;
            if (permission.getMenuType().equals(CommonConstant.MENU_TYPE_2) && CommonConstant.STATUS_1.equals(permission.getStatus())) {
                json = new JSONObject();
                json.put("action", permission.getPerms());
                json.put("type", permission.getPermsType());
                json.put("describe", permission.getName());
                jsonArray.add(json);
            }
        }
    }

    /**
     * 获取菜单JSON数组
     */
    private void getPermissionJsonArray(JSONArray jsonArray, List<Permission> metaList, JSONObject parentJson) {
        for (Permission permission : metaList) {
            if (permission.getMenuType() == null) {
                continue;
            }
            String tempPid = permission.getParentId();
            JSONObject json = getPermissionJsonObject(permission);
            if (json == null) {
                continue;
            }
            if (parentJson == null && oConvertUtils.isEmpty(tempPid)) {
                jsonArray.add(json);
                if (!permission.isLeaf()) {
                    getPermissionJsonArray(jsonArray, metaList, json);
                }
            } else if (parentJson != null && oConvertUtils.isNotEmpty(tempPid) && tempPid.equals(parentJson.getString("id"))) {
                // 类型( 0：一级菜单 1：子菜单 2：按钮 )
                if (permission.getMenuType().equals(CommonConstant.MENU_TYPE_2)) {
                    JSONObject metaJson = parentJson.getJSONObject("meta");
                    if (metaJson.containsKey("permissionList")) {
                        metaJson.getJSONArray("permissionList").add(json);
                    } else {
                        JSONArray permissionList = new JSONArray();
                        permissionList.add(json);
                        metaJson.put("permissionList", permissionList);
                    }
                    // 类型( 0：一级菜单 1：子菜单 2：按钮 )
                } else if (permission.getMenuType().equals(CommonConstant.MENU_TYPE_1) || permission.getMenuType().equals(CommonConstant.MENU_TYPE_0)) {
                    if (parentJson.containsKey("children")) {
                        parentJson.getJSONArray("children").add(json);
                    } else {
                        JSONArray children = new JSONArray();
                        children.add(json);
                        parentJson.put("children", children);
                    }

                    if (!permission.isLeaf()) {
                        getPermissionJsonArray(jsonArray, metaList, json);
                    }
                }
            }

        }
    }

    /**
     * 根据菜单配置生成路由json
     */
    private JSONObject getPermissionJsonObject(Permission permission) {
        JSONObject json = new JSONObject();
        // 类型(0：一级菜单 1：子菜单 2：按钮)
        if (permission.getMenuType().equals(CommonConstant.MENU_TYPE_2)) {
            //json.put("action", permission.getPerms());
            //json.put("type", permission.getPermsType());
            //json.put("describe", permission.getName());
            return null;
        } else if (permission.getMenuType().equals(CommonConstant.MENU_TYPE_0) || permission.getMenuType().equals(CommonConstant.MENU_TYPE_1)) {
            json.put("id", permission.getId());
            if (permission.isRoute()) {
                json.put("route", "1");// 表示生成路由
            } else {
                json.put("route", "0");// 表示不生成路由
            }

            if (isWWWHttpUrl(permission.getUrl())) {
                json.put("path", MD5Util.MD5Encode(permission.getUrl(), "utf-8"));
            } else {
                json.put("path", permission.getUrl());
            }

            // 重要规则：路由name (通过URL生成路由name,路由name供前端开发，页面跳转使用)
            if (oConvertUtils.isNotEmpty(permission.getComponentName())) {
                json.put("name", permission.getComponentName());
            } else {
                json.put("name", urlToRouteName(permission.getUrl()));
            }

            json.put("props", true);

            // 是否隐藏路由，默认都是显示的
            if (permission.isHidden()) {
                json.put("hidden", true);
            }
            // 聚合路由
            if (permission.isAlwaysShow()) {
                json.put("alwaysShow", true);
            }
            json.put("component", permission.getComponent());
            JSONObject meta = new JSONObject();
            // 由用户设置是否缓存页面 用布尔值
            meta.put("keepAlive", permission.isKeepAlive());

            /*update_begin author:wuxianquan date:20190908 for:往菜单信息里添加外链菜单打开方式 */
            //外链菜单打开方式
            meta.put("internalOrExternal", permission.isInternalOrExternal());
            /* update_end author:wuxianquan date:20190908 for: 往菜单信息里添加外链菜单打开方式*/

            meta.put("title", permission.getName());
            if (oConvertUtils.isEmpty(permission.getParentId())) {
                // 一级菜单跳转地址
                json.put("redirect", permission.getRedirect());
            }
            if (oConvertUtils.isNotEmpty(permission.getIcon())) {
                meta.put("icon", permission.getIcon());
            }
            if (isWWWHttpUrl(permission.getUrl())) {
                meta.put("url", permission.getUrl());
            }
            json.put("meta", meta);
        }

        return json;
    }

    /**
     * 判断是否外网URL 例如： http://localhost:8080/jeecg-boot/swagger-ui.html#/ 支持特殊格式： {{
     * window._CONFIG['domianURL'] }}/druid/ {{ JS代码片段 }}，前台解析会自动执行JS代码片段
     */
    private boolean isWWWHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("{{"));
    }

    /**
     * 通过URL生成路由name（去掉URL前缀斜杠，替换内容中的斜杠‘/’为-） 举例： URL = /isystem/role RouteName =
     * isystem-role
     */
    private String urlToRouteName(String url) {
        if (oConvertUtils.isNotEmpty(url)) {
            if (url.startsWith("/")) {
                url = url.substring(1);
            }
            url = url.replace("/", "-");

            // 特殊标记
            url = url.replace(":", "@");
            return url;
        } else {
            return null;
        }
    }

    /**
     * 根据菜单id来获取其对应的权限数据
     */
    @RequestMapping(value = "/getPermRuleListByPermId", method = RequestMethod.GET)
    public Result<List<PermissionDataRule>> getPermRuleListByPermId(PermissionDataRule permissionDataRule) {
        List<PermissionDataRule> permRuleList = sysPermissionDataRuleService.getPermRuleListByPermId(permissionDataRule.getPermissionId());
        Result<List<PermissionDataRule>> result = new Result<>();
        result.setSuccess(true);
        result.setResult(permRuleList);
        return result;
    }

    /**
     * 添加菜单权限数据
     */
    @RequiresRoles({"admin"})
    @RequestMapping(value = "/addPermissionRule", method = RequestMethod.POST)
    public Result<PermissionDataRule> addPermissionRule(@RequestBody PermissionDataRule permissionDataRule) {
        Result<PermissionDataRule> result = new Result<>();
        try {
            sysPermissionDataRuleService.savePermissionDataRule(permissionDataRule);
            result.success("添加成功！");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.error500("操作失败");
        }
        return result;
    }

    @RequiresRoles({"admin"})
    @RequestMapping(value = "/editPermissionRule", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<PermissionDataRule> editPermissionRule(@RequestBody PermissionDataRule permissionDataRule) {
        Result<PermissionDataRule> result = new Result<>();
        try {
            sysPermissionDataRuleService.saveOrUpdate(permissionDataRule);
            result.success("更新成功！");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.error500("操作失败");
        }
        return result;
    }

    /**
     * 删除菜单权限数据
     */
    @RequiresRoles({"admin"})
    @RequestMapping(value = "/deletePermissionRule", method = RequestMethod.DELETE)
    public Result<PermissionDataRule> deletePermissionRule(@RequestParam(name = "id") String id) {
        Result<PermissionDataRule> result = new Result<>();
        try {
            sysPermissionDataRuleService.deletePermissionDataRule(id);
            result.success("删除成功！");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.error500("操作失败");
        }
        return result;
    }

    /**
     * 查询菜单权限数据
     */
    @RequestMapping(value = "/queryPermissionRule", method = RequestMethod.GET)
    public Result<List<PermissionDataRule>> queryPermissionRule(PermissionDataRule permissionDataRule) {
        Result<List<PermissionDataRule>> result = new Result<>();
        try {
            List<PermissionDataRule> permRuleList = sysPermissionDataRuleService.queryPermissionRule(permissionDataRule);
            result.setResult(permRuleList);
            result.success("查询成功！");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.error500("操作失败");
        }
        return result;
    }

    /**
     * 部门权限表
     */
    @RequestMapping(value = "/queryDepartPermission", method = RequestMethod.GET)
    public Result<List<String>> queryDepartPermission(@RequestParam(name = "departId") String departId) {
        Result<List<String>> result = new Result<>();
        try {
            List<DepartPermission> list = sysDepartPermissionService.list(new QueryWrapper<DepartPermission>().lambda().eq(DepartPermission::getDepartId, departId));
            result.setResult(list.stream().map(SysDepartPermission -> String.valueOf(SysDepartPermission.getPermissionId())).collect(Collectors.toList()));
            result.setSuccess(true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return result;
    }

    /**
     * 保存部门授权
     */
    @RequestMapping(value = "/saveDepartPermission", method = RequestMethod.POST)
    @RequiresRoles({"admin"})
    public Result<String> saveDepartPermission(@RequestBody JSONObject json) {
        long start = System.currentTimeMillis();
        Result<String> result = new Result<>();
        try {
            String departId = json.getString("departId");
            String permissionIds = json.getString("permissionIds");
            String lastPermissionIds = json.getString("lastpermissionIds");
            this.sysDepartPermissionService.saveDepartPermission(departId, permissionIds, lastPermissionIds);
            result.success("保存成功！");
            log.info("======部门授权成功=====耗时:" + (System.currentTimeMillis() - start) + "毫秒");
        } catch (Exception e) {
            result.error500("授权失败！");
            log.error(e.getMessage(), e);
        }
        return result;
    }
}
