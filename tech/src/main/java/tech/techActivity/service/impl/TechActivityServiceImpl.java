package tech.techActivity.service.impl;

import auth.domain.dict.service.ISysDictItemService;
import auth.domain.dict.service.ISysDictService;
import auth.domain.user.service.ISysUserService;
import auth.entity.Dict;
import auth.entity.DictItem;
import auth.entity.User;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mysql.cj.util.StringUtils;
import commons.api.vo.Result;
import commons.auth.vo.LoginUser;
import commons.util.oConvertUtils;
import net.sf.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcode.config.exception.BusinessException;
import tech.constant.Constant;
import tech.signUp.entity.SignUp;
import tech.signUp.service.ISignUpService;
import tech.techActivity.entity.TechActivity;
import tech.techActivity.entity.TechField;
import tech.techActivity.mapper.TechActivityMapper;
import tech.techActivity.mapper.TechFieldMapper;
import tech.techActivity.service.ITechActivityService;
import tech.techActivity.vo.AccessToken;
import tech.utils.CommonUtil;
import tech.utils.HttpClientUtil;
import tech.utils.MyX509TrustManager;
import tech.utils.WxUtil;
import tech.wxUser.entity.WxUser;
import tech.wxUser.service.WxUserService;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Description: ?????????
 * @Author: zd-boot
 * @Date: 2020-12-02
 * @Version: V1.0
 */
@Service
public class TechActivityServiceImpl extends ServiceImpl<TechActivityMapper, TechActivity> implements ITechActivityService {

    @Autowired
    private TechActivityMapper techActivityMapper;
    @Autowired
    private TechFieldMapper techFieldMapper;
    @Autowired
    private WxUserService wxUserService;
    @Autowired
    private ISignUpService signUpService;
    @Autowired
    private ISysDictService sysDictService;
    @Autowired
    private ISysDictItemService sysDictItemService;
    @Autowired
    private ISysUserService sysUserService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMain(TechActivity techActivity, List<TechField> techFieldList) {
        techActivityMapper.insert(techActivity);
        if (techFieldList != null && techFieldList.size() > 0) {
            for (TechField entity : techFieldList) {
                //????????????
                entity.setTechId(techActivity.getId());
                techFieldMapper.insert(entity);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMain(TechActivity techActivity, List<TechField> techFieldList) {

        techActivityMapper.updateById(techActivity);
        if (("1").equals(techActivity.getAuditType()) && oConvertUtils.isNotEmpty(techActivity.getDeptCode())) {
            List<SignUp> signUpList = signUpService.list(new QueryWrapper<SignUp>().eq("tech_name", techActivity.getId()));

            signUpList.forEach(signUp -> {
                signUp.setCreateBy(techActivity.getDeptCode());
            });
            signUpService.updateBatchById(signUpList);
        }

        //1.?????????????????????
        techFieldMapper.deleteByMainId(techActivity.getId());

        //2.????????????????????????
        if (techFieldList != null && techFieldList.size() > 0) {
            for (TechField entity : techFieldList) {
                //????????????
                entity.setTechId(techActivity.getId());
                techFieldMapper.insert(entity);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delMain(String id) {
        techFieldMapper.deleteByMainId(id);
        techActivityMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delBatchMain(Collection<? extends Serializable> idList) {
        for (Serializable id : idList) {
            techFieldMapper.deleteByMainId(id.toString());
            techActivityMapper.deleteById(id);
        }
    }

    @Override
    public Result<?> saveCode(TechActivity techActivity) {
        if (oConvertUtils.isEmpty(techActivity.getAudit())) {
            String url = this.createForeverTicket(techActivity);
            techActivity.setUrl(url);
            baseMapper.updateById(techActivity);
            return Result.ok("????????????");
        } else if (("2").equals(techActivity.getAudit())) {
            String url = this.createForeverTicket(techActivity);
//		???????????????
            if (("1").equals(techActivity.getType())) {
                techActivity.setUrl(url);
            }
//		???????????????
            if (("2").equals(techActivity.getType())) {
                techActivity.setCancelUrl(url);
            }
            baseMapper.updateById(techActivity);
            return Result.ok("????????????");
        }
        return Result.error("????????????????????????????????????????????????????????????????????????");

    }

    /**
     * ??????????????????
     *
     * @param code
     * @return
     */
    @Override
    public String getOpenId(String code) {

        String url = String.format(Constant.userToken, Constant.appId, Constant.secret, code);

        //??????access_token
//		String url = "https://api.weixin.qq.com/sns/oauth2/access_token" +
//				"?appid=" + Constant.appId +
//				"&secret=" + Constant.secret +
//				"&code=" + code +
//				"&grant_type=authorization_code";

        net.sf.json.JSONObject resultObject = CommonUtil.httpsRequest(url, Constant.get, null);

        String infoUrl = String.format(Constant.userInfo, resultObject.getString("access_token"), resultObject.getString("openid"));

//		//????????????userInfo
//		String infoUrl = "https://api.weixin.qq.com/sns/userinfo" +
//				"" + resultObject.getString("access_token") +
//				"" + resultObject.getString("openid") +
//				"";

        net.sf.json.JSONObject resultInfo = CommonUtil.httpsRequest(infoUrl, Constant.get, null);

        return resultObject.getString("openid");
    }

    @Override
    public Result<?> queryById(String id, String openId) {
        TechActivity techActivity = this.getById(id);
        List<TechField> techFieldList = techFieldMapper.selectList(new QueryWrapper<TechField>().eq("tech_id", id));
        if (openId != null) {
            SignUp signUp = signUpService.getOne(new QueryWrapper<SignUp>().eq("open_id", openId).eq("tech_name", id));
            if (signUp == null) {
                WxUser wxUser = wxUserService.getOne(new QueryWrapper<WxUser>().eq("open_id", openId).eq("del_flag", 0));
                signUp = new SignUp();
                if (wxUser != null && wxUser.getName() != null) {
                    signUp.setName(wxUser.getName());
                    signUp.setPhoneNumber(wxUser.getPhoneNumber());
                    signUp.setUnitName(wxUser.getUnitName());
                }
                signUp.setId("");
                if (techFieldList != null && techFieldList.size() > 0) {
                    techFieldList.forEach(techField -> {
                        if (("3").equals(techField.getFieldType()) || ("4").equals(techField.getFieldType())) {
                            if (oConvertUtils.isNotEmpty(techField.getFieldDict())) {
                                Dict dict = sysDictService.getOne(new QueryWrapper<Dict>().eq("dict_code", techField.getFieldDict()));
                                List<DictItem> dictItemList = sysDictItemService.list(new QueryWrapper<DictItem>().eq("dict_id", dict.getId()));
                                if (oConvertUtils.isNotEmpty(techField.getTest())) {
                                    List<DictItem> itemList = dictItemList.stream().filter(dictItem -> dictItem.getItemValue().equals(techField.getTest())).collect(Collectors.toList());
                                    techField.setItemText(itemList.get(0).getItemText());
                                }
                                techField.setDictList(dictItemList);
                            }
                        }
                    });
                }
                signUp.setTechFieldList(techFieldList);
            } else {
                if (oConvertUtils.isNotEmpty(signUp.getFieldTest())) {
                    JSONArray jsonArray = JSONArray.fromObject(signUp.getFieldTest());
                    List<TechField> fields = JSONObject.parseArray(jsonArray.toString(), TechField.class);
                    fields.forEach(techField -> {
                        if (("3") .equals(techField.getFieldType())|| ("4").equals(techField.getFieldType())) {
                            if (oConvertUtils.isNotEmpty(techField.getFieldDict())) {
                                Dict dict = sysDictService.getOne(new QueryWrapper<Dict>().eq("dict_code", techField.getFieldDict()));
                                List<DictItem> dictItemList = sysDictItemService.list(new QueryWrapper<DictItem>().eq("dict_id", dict.getId()));
                                if (oConvertUtils.isNotEmpty(techField.getTest()) && techField.getFieldType().equals("3")) {
                                    List<DictItem> itemList = dictItemList.stream().filter(dictItem -> dictItem.getItemValue().equals(techField.getTest())).collect(Collectors.toList());
                                    techField.setItemText(itemList.get(0).getItemText());
                                }
                                techField.setDictList(dictItemList);
                            }
                        }
                    });
                    signUp.setTechFieldList(fields);
                }
            }
            techActivity.setSignUp(signUp);
        }
        return Result.ok(techActivity);
    }

    /**
     * ??????????????????
     *
     * @param request
     * @param response
     * @throws IOException
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = response.getWriter();
        Map<String, String> returnMap = new HashMap<>();
        try {
            Map<String, String> map = WxUtil.xmlToMap(request);
            String toUserName = map.get("ToUserName");
            String fromUserName = map.get("FromUserName");
            String msgType = map.get("MsgType");
            String content = map.get("Content");
            String encryptMsg = null;
            if ("???????????????".equals(content)) {
                // ??????????????????
                returnMap.put("ToUserName", fromUserName);
                returnMap.put("FromUserName", toUserName);
                returnMap.put("CreateTime", (new Date()).getTime() + "");
                returnMap.put("MsgType", "text");
                returnMap.put("Content", "http://register.stcsm.sh.gov.cn/login");
                encryptMsg = WxUtil.mapToXml(returnMap);
                printWriter.print(encryptMsg);
            }
            if (Constant.event.equals(msgType)) {
                String event = map.get("Event");
//                ?????????????????????
                if (Constant.scan.equals(event)) {
                    String eventKey = map.get("EventKey");
                    String[] split = eventKey.split("&type=");
                    String id = split[0].split("id=")[1];
                    TechActivity techActivity = this.getById(id);
                    returnMap.put("ToUserName", fromUserName);
                    returnMap.put("FromUserName", toUserName);
                    returnMap.put("CreateTime", (new Date()).getTime() + "");
                    returnMap.put("MsgType", "text");
                    if (split[1].equals("1")) {
                        returnMap.put("Content", "????????????:" +techActivity.getHeadline() +"\n\r"+"??????????????????:"+split[0]);
                    } else {
                        returnMap.put("Content", "????????????:" + techActivity.getHeadline() + "\n\r" + "<a href='" + Constant.dist + "appointmentSuccess" + "&openId=" + fromUserName + "'>????????????</a>");
                    }

                    //??????
                    encryptMsg = WxUtil.mapToXml(returnMap);
                    System.out.print(encryptMsg);
                    printWriter.print(encryptMsg);
                } else if (Constant.subscribe.equals(event)) {
                    // ???????????? ??? ??????????????????????????????
                    // ???????????????ToUserName?????????FromUserName?????????
                    WxUser wxUser = wxUserService.getOne(new QueryWrapper<WxUser>().eq("open_id", fromUserName));
                    //????????????????????????
                    if (wxUser == null) {
                        //????????????????????????
                        String infoUrl = String.format(Constant.userInfo, CommonUtil.accessToken.getAccessToken(), fromUserName);
                        net.sf.json.JSONObject resultInfo = CommonUtil.httpsRequest(infoUrl, Constant.get, null);
                        wxUser = JSONObject.parseObject(String.valueOf(resultInfo), WxUser.class);
                        wxUser.setOpenId(fromUserName);
                        wxUser.setStatus("1");
                    }
                    wxUser.setDelFlag("0");
                    wxUserService.saveOrUpdate(wxUser);

                    returnMap.put("ToUserName", fromUserName);
                    returnMap.put("FromUserName", toUserName);
                    returnMap.put("CreateTime", (new Date()).getTime() + "");
                    returnMap.put("MsgType", "text");
                    returnMap.put("Content", "?????????????????????????????????????????????");
                    //??????
                    encryptMsg = WxUtil.mapToXml(returnMap);
                    printWriter.print(encryptMsg);

                } else if (Constant.unsubscribe.equals(event)) {
                    // ??????????????????
                    WxUser wxUser = wxUserService.getOne(new QueryWrapper<WxUser>().eq("open_id", fromUserName));
                    if (wxUser != null) {
                        wxUser.setDelFlag("1");
                        wxUserService.updateById(wxUser);
                    }
                } else if ("CLICK".equals(event)) {
                    // ??????????????????????????????????????????
                    // ????????????
                    String eventKey = map.get("EventKey");
                    if (eventKey != null ) {
                        returnMap.put("ToUserName", fromUserName);
                        returnMap.put("FromUserName", toUserName);
                        returnMap.put("CreateTime", (new Date()).getTime() + "");
                        returnMap.put("MsgType", "text");
                        if("waizhuan".equals(eventKey)){
                            returnMap.put("Content", "?????????????????????????????????????????????????????????????????????????????????????????????"+
                                    "??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????"+
                                    "???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                        }

                        if("zonghezixun".equals(eventKey)){
                            returnMap.put("Content", "????????????????????????????????????????????????????????????????????????????????????????????????"+
                                    "??????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                        }
                        if("zhongxinjieshao".equals(eventKey)){
                            returnMap.put("Content", "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????"+
                                    "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????"+
                                    "????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????");
                        }
                        if("fuwudidian".equals(eventKey)){
                            returnMap.put("Content", "?????????????????????????????????????????????1525?????????????????????");
                        }
                        if("fuwushijian".equals(eventKey)){
                            returnMap.put("Content", "??????????????????????????????  ??????09:00-11:30?????????????????????11:15????????????13:30-16:30?????????????????????16:15???");
                        }
                        encryptMsg = WxUtil.mapToXml(returnMap);
                        printWriter.print(encryptMsg);
                    }

//					System.out.println("aaa");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            printWriter.close();
        }

    }

    @Override
    public void download(String id, String type, HttpServletRequest request, HttpServletResponse response) throws MalformedURLException {
        TechActivity techActivity = baseMapper.selectById(id);
        String activityUrl = new String();
        if (type.equals("1")) {
            if (StringUtils.isNullOrEmpty(techActivity.getUrl())) {
                return;
            }
            activityUrl = techActivity.getUrl();
        }

        if (type.equals("2")) {
            if (StringUtils.isNullOrEmpty(techActivity.getCancelUrl())) {
                return;
            }
            activityUrl = techActivity.getCancelUrl();
        }
        int bytesum = 0;
        int byteread = 0;
        URL url = new URL(activityUrl);
        try {
            //??????????????????
            TrustManager[] tm = {new MyX509TrustManager()};
            SSLContext sslContext = SSLContext.getInstance("SSL", "SunJSSE");
            sslContext.init(null, tm, new SecureRandom());
            // ?????????SSLContext???????????????SSLSocketFactory??????
            SSLSocketFactory ssf = sslContext.getSocketFactory();
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setSSLSocketFactory(ssf);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod(Constant.get);
            InputStream inStream = conn.getInputStream();
            OutputStream outputStream = response.getOutputStream();
            byte[] buffer = new byte[1204];
            while ((byteread = inStream.read(buffer)) != -1) {
                bytesum += byteread;
                outputStream.write(buffer, 0, byteread);
            }
            outputStream.flush();
            outputStream.close();
            inStream.close();
        } catch (NoSuchProviderException | KeyManagementException | NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public IPage<TechActivity> getPage(Page<TechActivity> page, TechActivity techActivity, LoginUser sysUser) {
        return baseMapper.getPage(page, techActivity, sysUser);
    }

    @Override
    public void getTemplate(TechActivity techActivity, String templateId, String value) {
        List<String> list = Arrays.asList(techActivity.getDeptCode().split(","));
        List<User> users = sysUserService.list(new QueryWrapper<User>().in("username", list));
        users.forEach(user -> {
            if (oConvertUtils.isNotEmpty(user.getThirdId())) {
                AccessToken accessToken = CommonUtil.accessToken;
                JSONObject jsonObject = new JSONObject();
                //??????openid
                jsonObject.put("touser", user.getThirdId());
                jsonObject.put("template_id", templateId);
//                        jsonObject.put("url", Constant.dist + "?/eventdetails?id=" + techActivity.getId() + "&openId=" + user.getThirdId());
                JSONObject data = new JSONObject();
                JSONObject first = new JSONObject();
                first.put("value", value);
                data.put("first", first);
                JSONObject keyword1 = new JSONObject();
                keyword1.put("value", "????????????");
                data.put("keyword1", keyword1);

                SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                JSONObject keyword2 = new JSONObject();
                keyword2.put("value", techActivity.getHeadline());
                data.put("keyword2", keyword2);

                JSONObject keyword3 = new JSONObject();
                keyword3.put("value", sf.format(new Date()));
                data.put("keyword3", keyword3);

//                JSONObject keyword4 = new JSONObject();
//                keyword4.put("value", techActivity.getPlace());
//                data.put("keyword4", keyword4);

                jsonObject.put("data", data);

                net.sf.json.JSONObject jsonObject1 = CommonUtil.httpsRequest(Constant.templateUrl + accessToken.getAccessToken(), "POST", JSONObject.toJSONString(jsonObject));

            }
        });
    }

    /**
     * ?????????????????????
     *
     * @param headline
     * @param place
     * @param startTime
     * @return
     */
    @Override
    public List<TechActivity> appList(String headline, String place, String startTime, String status) {
        return baseMapper.appList(headline, place, startTime, status);
    }

    @Override
    public void getTemplate1(TechActivity techActivity, String templateId, String value) {
        List<String> list = Arrays.asList(techActivity.getDeptCode().split(","));
        List<User> users = sysUserService.list(new QueryWrapper<User>().in("username", list));
        users.forEach(user -> {
            if (oConvertUtils.isNotEmpty(user.getThirdId())) {
                AccessToken accessToken = CommonUtil.accessToken;
                JSONObject jsonObject = new JSONObject();
                //??????openid
                jsonObject.put("touser", user.getThirdId());
                jsonObject.put("template_id", templateId);
//                        jsonObject.put("url", Constant.dist + "?/eventdetails?id=" + techActivity.getId() + "&openId=" + user.getThirdId());
                JSONObject data = new JSONObject();
                JSONObject first = new JSONObject();
                first.put("value", value);
                data.put("first", first);

                JSONObject keyword1 = new JSONObject();
                keyword1.put("value", techActivity.getHeadline());
                data.put("keyword1", keyword1);

                JSONObject keyword2 = new JSONObject();
                if (("2").equals(techActivity.getAudit())) {
                    keyword2.put("value", "?????????????????????");
                }
                if (("3").equals(techActivity.getAudit())) {
                    keyword2.put("value", "?????????????????????");
                }
                data.put("keyword2", keyword2);

//
//                JSONObject keyword3 = new JSONObject();
//                keyword3.put("value", new Date());
//                data.put("keyword3", keyword3);

//                JSONObject keyword4 = new JSONObject();
//                keyword4.put("value", techActivity.getPlace());
//                data.put("keyword4", keyword4);

                jsonObject.put("data", data);

                net.sf.json.JSONObject jsonObject1 = CommonUtil.httpsRequest(Constant.templateUrl + accessToken.getAccessToken(), "POST", JSONObject.toJSONString(jsonObject));

            }
        });
    }


    /**
     * ??????????????????????????????
     *
     * @param openId
     * @return Boolean ,true???????????????false????????????
     * @throws BusinessException
     */
    public Boolean isXSNSubscribe(String openId) throws BusinessException {
        // ?????????access_token
        String access_token = CommonUtil.accessToken.getAccessToken();
        // ????????????
        String url = String.format("https://api.weixin.qq.com/cgi-bin/user/info?access_token=%s&openid=%s&lang=zh_CN", access_token, openId);
        String result = HttpClientUtil.doGet(url, null);
        JSONObject json = JSON.parseObject(result);
        return "1".equals(json.getString("subscribe"));
    }

    /**
     * ?????????????????????(?????????)
     *
     * @param techActivity
     * @return
     */
    private String createForeverTicket(TechActivity techActivity) {
        String qrcodeUrl = "";
        try {
            AccessToken accessToken = CommonUtil.accessToken;
            JSONObject jsonObject = new JSONObject();
            JSONObject actionInfo = new JSONObject();
            JSONObject scene = new JSONObject();
            jsonObject.put("expire_seconds", Constant.expire_seconds);
            jsonObject.put("action_name", Constant.QR_SCENE2);
            //			????????????
            if (("1").equals(techActivity.getType())) {
                scene.put("scene_str", Constant.dist + "eventdetails?id=" + techActivity.getId() + "&type=1");
            }
//			????????????
            if (("2").equals(techActivity.getType())) {
                scene.put("scene_str", Constant.dist + "audit?id=" + techActivity.getId() + "&type=2");
            }
            actionInfo.put("scene", scene);
            jsonObject.put("action_info", actionInfo);
            String url = Constant.tokenUrl + accessToken.getAccessToken();
            net.sf.json.JSONObject resultJson = CommonUtil.httpsRequest(url, "POST", jsonObject.toJSONString());
            String ticket = resultJson.getString("ticket");
            qrcodeUrl = Constant.codeUrl + URLEncoder.encode(ticket, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        System.out.println(qrcodeUrl);
        return qrcodeUrl;
    }

}
