package webapi.authController;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xkcoding.justauth.AuthRequestFactory;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import auth.domain.user.service.DefUserService;
import auth.domain.user.service.UserService;
import auth.entity.User;
import commons.api.vo.Result;
import commons.constant.CommonConstant;
import commons.auth.utils.JwtUtil;
import commons.system.api.ISysBaseAPI;
import commons.util.PasswordUtil;
import commons.util.RedisUtil;
import commons.util.oConvertUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/thirdLogin")
@Slf4j
public class ThirdLoginController {

    private final ISysBaseAPI sysBaseAPI;

    private final RedisUtil redisUtil;

    private final AuthRequestFactory factory;

    private final UserService userService;

    private final DefUserService defUserService;

    @Autowired
    public ThirdLoginController(ISysBaseAPI sysBaseAPI, RedisUtil redisUtil, AuthRequestFactory factory, UserService userService, DefUserService defUserService) {
        this.sysBaseAPI = sysBaseAPI;
        this.redisUtil = redisUtil;
        this.factory = factory;
        this.userService = userService;
        this.defUserService = defUserService;
    }

    @RequestMapping("/render/{source}")
    public void render(@PathVariable("source") String source, HttpServletResponse response) throws IOException {
        log.info("?????????????????????render???" + source);
        AuthRequest authRequest = factory.get(source);
        String authorizeUrl = authRequest.authorize(AuthStateUtils.createState());
        log.info("??????????????????????????????" + authorizeUrl);
        response.sendRedirect(authorizeUrl);
    }

    @RequestMapping("/{source}/callback")
    public String login(@PathVariable("source") String source, AuthCallback callback, ModelMap modelMap) {
        log.info("?????????????????????callback???" + source + " params???" + JSONObject.toJSONString(callback));
        AuthRequest authRequest = factory.get(source);
        AuthResponse response = authRequest.login(callback);
        log.info(JSONObject.toJSONString(response));
        Result<JSONObject> result = new Result<>();
        if (response.getCode() == 2000) {

            JSONObject data = JSONObject.parseObject(JSONObject.toJSONString(response.getData()));
            String username = data.getString("username");
            String avatar = data.getString("avatar");
            String uuid = data.getString("uuid");

            //????????????????????????
            LambdaQueryWrapper<User> query = new LambdaQueryWrapper<>();
            query.eq(User::getThirdId, uuid);
            query.eq(User::getThirdType, source);
            List<User> thirdList = defUserService.list(query);
            User user;
            if (thirdList == null || thirdList.size() == 0) {
                user = new User();
                user.setActivitiSync(CommonConstant.ACT_SYNC_0);
                user.setDelFlag(CommonConstant.DEL_FLAG_0);
                user.setStatus(1);
                user.setThirdId(uuid);
                user.setThirdType(source);
                user.setAvatar(avatar);
                user.setUsername(uuid);
                user.setRealname(username);

                //??????????????????
                String salt = oConvertUtils.randomGen(8);
                user.setSalt(salt);
                String passwordEncode = PasswordUtil.encrypt(user.getUsername(), "123456", salt);
                user.setPassword(passwordEncode);
                userService.saveThirdUser(user);
            } else {
                //????????? ?????????????????? ???????????????
                user = thirdList.get(0);
                user.setUsername(username);
                defUserService.updateById(user);
            }
            // ??????token
            String token = JwtUtil.sign(user.getUsername(), user.getPassword());
            redisUtil.set(CommonConstant.PREFIX_USER_TOKEN + token, token);
            // ??????????????????
            redisUtil.expire(CommonConstant.PREFIX_USER_TOKEN + token, JwtUtil.EXPIRE_TIME / 1000);
            modelMap.addAttribute("token", token);

        }
        result.setSuccess(false);
        result.setMessage("?????????????????????,??????????????????");
        return "thirdLogin";
    }

    @SuppressWarnings("unchecked")
    @RequestMapping(value = "/getLoginUser/{token}", method = RequestMethod.GET)
    @ResponseBody
    public Result<JSONObject> getLoginUser(@PathVariable("token") String token) {
        Result<JSONObject> result;
        String username = JwtUtil.getUsername(token);

        //1. ????????????????????????
        User User = userService.getUserByName(username);
        result = userService.checkUserIsEffective(User);
        if (!result.isSuccess()) {
            return result;
        }
        JSONObject obj = new JSONObject();
        //??????????????????
        obj.put("userInfo", User);
        //token ??????
        obj.put("token", token);
        result.setResult(obj);
        result.setSuccess(true);
        result.setCode(200);
        sysBaseAPI.addLog("?????????: " + username + ",????????????[???????????????]???", CommonConstant.LOG_TYPE_1, null);
        return result;
    }

}
