package com.dx168.patchserver.manager.web;

import com.alibaba.fastjson.JSON;
import com.dx168.patchserver.core.domain.*;
import com.dx168.patchserver.core.utils.BizAssert;
import com.dx168.patchserver.core.utils.BizException;
import com.dx168.patchserver.core.utils.HttpRequestUtils;
import com.dx168.patchserver.manager.common.Constants;
import com.dx168.patchserver.manager.common.RestResponse;
import com.dx168.patchserver.manager.service.*;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageInfo;
import net.glxn.qrgen.javase.QRCode;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.util.Base64Utils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by tong on 15/10/24.
 */
@Controller
public class ManagerController {

    private org.slf4j.Logger logger = LoggerFactory.getLogger(ManagerController.class);

    @Autowired
    private AppService appService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private PatchService patchService;

    @Autowired
    private TesterService testerService;

    @Autowired
    private ModelBlacklistService modelBlacklistService;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private FullUpdateService fullUpdateService;

    @Autowired
    private PatchLogService patchLogService;

    @Value("${spring.http.multipart.max-file-size}")
    private String maxPatchSize;

    @RequestMapping("/404")
    public String pageNotFound() {
        return "404";
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ModelAndView index() {
        return new ModelAndView("redirect:/app/list");
    }

    @RequestMapping(value = "/app/list", method = RequestMethod.GET)
    public ModelAndView index(HttpServletRequest req) {
        RestResponse restR = new RestResponse();

        BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
        List<AppInfo> appInfoList = appService.findAllAppInfoByUser(basicUser);
        restR.getData().put("user", basicUser);
        restR.getData().put("appInfoList", appInfoList);
        return new ModelAndView("app_list", "restR", restR);
    }

    @RequestMapping(value = "/app/create", method = RequestMethod.POST)
    public @ResponseBody
    RestResponse app_create(HttpServletRequest req, String appname, String description, String packageName) {
        RestResponse restR = new RestResponse();
        try {
            BizAssert.notEpmty(appname, "应用名不能为空");
            BizAssert.notEpmty(description, "描述信息不能为空");
            BizAssert.notEpmty(packageName, "包名不能为空");

            if (packageName.matches("[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(/.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+/.?$")) {
                throw new BizException("包名格式不正确");
            }
            BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
            if (basicUser.isChildAccount()) {
                throw new BizException("没有权限创建应用");
            }
            appService.addApp(basicUser, appname, description, packageName, "Android");
        } catch (BizException e) {
            restR.setCode(-1);
            restR.setMessage(e.getMessage());
        }
        return restR;
    }

    @RequestMapping(value = "/app/delete", method = RequestMethod.POST)
    public @ResponseBody
    RestResponse deleteApp(HttpServletRequest req, String appUid) {
        RestResponse restR = new RestResponse();
        BizAssert.notEpmty(appUid, "应用编号不能为空");
        appService.deleteApp(appUid);

        return restR;
    }

    @RequestMapping(value = "/app/fill_package", method = RequestMethod.POST)
    public @ResponseBody
    RestResponse fill_package(HttpServletRequest req, String appUid, String packageName) {
        RestResponse restR = new RestResponse();
        try {
            BizAssert.notEpmty(appUid, "应用编号不能为空");
            BizAssert.notEpmty(packageName, "包名不能为空");
            if (packageName.matches("[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(/.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+/.?$")) {
                throw new BizException("包名格式不正确");
            }

            BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
            appService.fillPackageName(basicUser, appUid, packageName);
        } catch (BizException e) {
            restR.setCode(-1);
            restR.setMessage(e.getMessage());
        }
        return restR;
    }

    @RequestMapping(value = "/app", method = RequestMethod.GET)
    public ModelAndView app(HttpServletRequest req, String appUid) {
        RestResponse restR = new RestResponse();
        BizAssert.notEpmty(appUid, "应用编号不能为空");
        AppInfo appInfo = appService.findByUid(appUid);
        BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
        List<AppInfo> appInfoList = appService.findAllAppInfoByUser(basicUser);
        restR.getData().put("user", basicUser);
        restR.getData().put("appInfo", appInfo);
        restR.getData().put("appInfoList", appInfoList);
        restR.getData().put("versionList", appService.findAllVersion(appInfo));
        return new ModelAndView("app", "restR", restR);
    }

    @RequestMapping(value = "/tester/list", method = RequestMethod.GET)
    public ModelAndView tester_list(HttpServletRequest req, String appUid) {
        RestResponse restR = new RestResponse();
        BizAssert.notEpmty(appUid, "应用编号不能为空");
        AppInfo appInfo = appService.findByUid(appUid);
        BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
        restR.getData().put("user", basicUser);
        restR.getData().put("appInfo", appInfo);
        restR.getData().put("testerList", testerService.findAllByAppUid(appUid));
        restR.getData().put("appInfoList", appService.findAllAppInfoByUser(basicUser));
        return new ModelAndView("tester_list", "restR", restR);
    }

    @RequestMapping(value = "/tester/add", method = RequestMethod.POST)
    public @ResponseBody
    RestResponse addTester(HttpServletRequest req, String appUid, String tag, String email, String description) {
        RestResponse restR = new RestResponse();
        try {
            BizAssert.notEpmty(appUid, "应用号不能为空");
            BizAssert.notEpmty(tag, "tag不能为空");
            BizAssert.notEpmty(email, "email不能为空");
            BizAssert.notEpmty(tag, "版本号不能为空");

            Tester tester = testerService.findByTagAndUid(tag, appUid);
            if (tester != null) {
                throw new BizException("测试tag已存在");
            }
            BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
            tester = new Tester();
            tester.setUserId(accountService.getRootUserId(basicUser));
            tester.setAppUid(appUid);
            tester.setTag(tag);
            tester.setEmail(email);
            tester.setDescription(description);

            testerService.save(tester);
        } catch (BizException e) {
            restR.setCode(-1);
            restR.setMessage(e.getMessage());
        }

        return restR;
    }

    @RequestMapping(value = "/tester/del", method = RequestMethod.POST)
    public @ResponseBody
    RestResponse delTester(HttpServletRequest req, Integer testerId) {
        RestResponse restR = new RestResponse();
        try {
            BizAssert.notNull(testerId, "id不能为空");

            BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
            Tester tester = testerService.findById(testerId);
            if (tester == null || accountService.getRootUserId(basicUser) != tester.getUserId()) {
                throw new BizException("信息不存在");
            }
            testerService.deleteById(testerId);
        } catch (BizException e) {
            restR.setCode(-1);
            restR.setMessage(e.getMessage());
        }

        return restR;
    }

    @RequestMapping(value = "/modelblacklist/list", method = RequestMethod.GET)
    public ModelAndView tester_list(HttpServletRequest req) {
        RestResponse restR = new RestResponse();
        BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
        List<Model> modelList = modelBlacklistService.findAllByUserId(accountService.getRootUserId(basicUser));

        restR.getData().put("user", basicUser);
        restR.getData().put("modelBlackList", modelList);
        restR.getData().put("appInfoList", appService.findAllAppInfoByUser(basicUser));
        return new ModelAndView("model_blacklist", "restR", restR);
    }

    @RequestMapping(value = "/modelblacklist/add", method = RequestMethod.POST)
    public @ResponseBody
    RestResponse add_modelblacklist(HttpServletRequest req, String regularExp, String description) {
        RestResponse restR = new RestResponse();
        try {
            BizAssert.notEpmty(regularExp, "正则表达式不能为空");
            BizAssert.notEpmty(description, "描述不能为空");
            try {
                Pattern.compile(regularExp);
            } catch (Throwable e) {
                throw new BizException("无效的正则表达式");
            }

            BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
            Model model = modelBlacklistService.findByRegexp(accountService.getRootUserId(basicUser), regularExp);
            if (model != null) {
                throw new BizException("匹配该机型的正则已存在");
            }
            model = new Model();
            model.setUserId(accountService.getRootUserId(basicUser));
            model.setRegularExp(regularExp);
            model.setDescription(description);

            modelBlacklistService.save(model);
        } catch (BizException e) {
            restR.setCode(-1);
            restR.setMessage(e.getMessage());
        }

        return restR;
    }

    @RequestMapping(value = "/modelblacklist/del", method = RequestMethod.POST)
    public @ResponseBody
    RestResponse del_modelblacklist(HttpServletRequest req, Integer modelblackId) {
        RestResponse restR = new RestResponse();
        try {
            BizAssert.notNull(modelblackId, "id不能为空");

            BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
            Model model = modelBlacklistService.findById(modelblackId);
            if (model == null || accountService.getRootUserId(basicUser) != model.getUserId()) {
                throw new BizException("信息不存在");
            }
            modelBlacklistService.delete(model);
        } catch (BizException e) {
            restR.setCode(-1);
            restR.setMessage(e.getMessage());
        }

        return restR;
    }

    @RequestMapping(value = "/channel/list", method = RequestMethod.GET)
    public ModelAndView channel_list(HttpServletRequest req) {
        RestResponse restR = new RestResponse();
        BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
        List<Channel> modelList = channelService.findAllByUserId(accountService.getRootUserId(basicUser));

        restR.getData().put("user", basicUser);
        restR.getData().put("channelList", modelList);
        restR.getData().put("appInfoList", appService.findAllAppInfoByUser(basicUser));
        return new ModelAndView("channel_list", "restR", restR);
    }

    @RequestMapping(value = "/channel/add", method = RequestMethod.POST)
    public @ResponseBody
    RestResponse add_channel(HttpServletRequest req, String channelName, String description) {
        RestResponse restR = new RestResponse();
        try {
            BizAssert.notEpmty(channelName, "渠道名称不能为空");
            BizAssert.notEpmty(description, "描述不能为空");
            BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
            Channel channel = channelService.findByUserIdAndName(accountService.getRootUserId(basicUser), channelName);
            if (channel != null) {
                throw new BizException("该渠道已存在");
            }
            channel = new Channel();
            channel.setUserId(accountService.getRootUserId(basicUser));
            channel.setChannelName(channelName);
            channel.setDescription(description);
            channelService.save(channel);
        } catch (BizException e) {
            restR.setCode(-1);
            restR.setMessage(e.getMessage());
        }

        return restR;
    }

    @RequestMapping(value = "/channel/del", method = RequestMethod.POST)
    public @ResponseBody
    RestResponse del_channel(HttpServletRequest req, Integer channelId) {
        RestResponse restR = new RestResponse();
        try {
            BizAssert.notNull(channelId, "id不能为空");

            BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
            Channel channel = channelService.findById(channelId);
            if (channel == null || accountService.getRootUserId(basicUser) != channel.getUserId()) {
                throw new BizException("信息不存在");
            }
            channelService.delete(channel);
        } catch (BizException e) {
            restR.setCode(-1);
            restR.setMessage(e.getMessage());
        }

        return restR;
    }

    @RequestMapping(value = "/app/create_version", method = RequestMethod.POST)
    public @ResponseBody
    RestResponse addVersion(HttpServletRequest req, String appUid, String versionName) {
        RestResponse restR = new RestResponse();
        try {
            BizAssert.notEpmty(appUid, "应用号不能为空");
            BizAssert.notEpmty(versionName, "版本号不能为空");
            restR.getData().put("appUid", appUid);
            AppInfo appInfo = appService.findByUid(appUid);
            VersionInfo versionInfo = appService.findVersionByUidAndVersionName(appInfo, versionName);
            if (versionInfo != null) {
                throw new BizException("此版本已存在");
            }
            BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
            versionInfo = new VersionInfo();
            versionInfo.setUserId(accountService.getRootUserId(basicUser));
            versionInfo.setAppUid(appUid);
            versionInfo.setVersionName(versionName);
            appService.saveVersionInfo(versionInfo);
        } catch (BizException e) {
            restR.setCode(-1);
            restR.setMessage(e.getMessage());
        }

        return restR;
    }

    @RequestMapping(value = "/app/version", method = RequestMethod.GET)
    public ModelAndView app_version(HttpServletRequest req, String appUid, String versionName) {
        RestResponse restR = new RestResponse();
        BizAssert.notEpmty(appUid, "应用号不能为空");
        BizAssert.notEpmty(versionName, "版本号不能为空");

        AppInfo appInfo = appService.findByUid(appUid);
        VersionInfo versionInfo = appService.findVersionByUidAndVersionName(appInfo, versionName);
        if (versionInfo == null) {
            throw new BizException("该版本未找到: " + versionName);
        }
        //加载所有patch信息
        List<PatchInfo> patchInfoList = patchService.findByUidAndVersionName(appUid, versionName);

        restR.getData().put("appInfo", appInfo);
        restR.getData().put("versionInfo", versionInfo);
        restR.getData().put("patchInfoList", patchInfoList);

        BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
        List<AppInfo> appInfoList = appService.findAllAppInfoByUser(basicUser);
        restR.getData().put("user", basicUser);
        restR.getData().put("appInfoList", appInfoList);
        restR.getData().put("versionList", appService.findAllVersion(appInfo));
        restR.getData().put("maxPatchSize", maxPatchSize);
        return new ModelAndView("version", "restR", restR);
    }

    @RequestMapping(value = "/patch/add", method = RequestMethod.GET)
    public ModelAndView on_patch_add_session_time_out(String appUid, String versionName) {
        return new ModelAndView("redirect:/app/version?appUid=" + appUid + "&versionName=" + versionName);
    }

    @RequestMapping(value = "/patch/add", method = RequestMethod.POST)
    public ModelAndView patch_create(String appUid, String versionName, String description, @RequestParam("file") MultipartFile multipartFile) {
        RestResponse restR = new RestResponse();
        try {
            BizAssert.notEpmty(appUid, "应用号不能为空");
            BizAssert.notEpmty(versionName, "版本号不能为空");
            BizAssert.notEpmty(description, "描述不能为空");
            BizAssert.notNull(multipartFile, "请选择文件");

            AppInfo appInfo = appService.findByUid(appUid);
            VersionInfo versionInfo = appService.findVersionByUidAndVersionName(appInfo, versionName);
            if (versionInfo == null) {
                throw new BizException("该版本未找到: " + versionName);
            }

            patchService.savePatch(appInfo, versionInfo, description, multipartFile);

            return new ModelAndView("redirect:/app/version?appUid=" + appUid + "&versionName=" + versionName);
        } catch (BizException e) {
            restR.setCode(-1);
            restR.setMessage(e.getMessage());
            return new ModelAndView("redirect:/app/version?appUid=" + appUid + "&versionName=" + versionName + "&msg=" + HttpRequestUtils.urlEncode(e.getMessage()));
        }
    }

    @RequestMapping(value = "/patch", method = RequestMethod.GET)
    public ModelAndView patch_detail(HttpServletRequest req, Integer id, String appUid) {
        RestResponse restR = new RestResponse();
        BizAssert.notNull(id, "参数不能为空");
        PatchInfo patchInfo = patchService.findByIdAndAppUid(id, appUid);
        if (patchInfo == null) {
            throw new BizException("参数不正确");
        }
        if (patchInfo.getStatus() == PatchInfo.STATUS_UNPUBLISHED) {
            String tags = testerService.getAllTags(appUid);
            if (!StringUtils.isEmpty(tags)) {
                restR.getData().put("tags", tags + ";");
            }
        }
        AppInfo appInfo = appService.findByUid(patchInfo.getAppUid());
        VersionInfo versionInfo = appService.findVersionByUidAndVersionName(appInfo, patchInfo.getVersionName());
        if (versionInfo == null) {
            throw new BizException("该版本未找到: " + patchInfo.getVersionName());
        }
        BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
        List<AppInfo> appInfoList = appService.findAllAppInfoByUser(basicUser);
        restR.getData().put("user", basicUser);
        restR.getData().put("appInfoList", appInfoList);
        restR.getData().put("appInfo", appInfo);
        restR.getData().put("versionInfo", versionInfo);
        restR.getData().put("patchInfo", patchInfo);

        if (StringUtils.isNotBlank(appInfo.getPackageName())) {
            ByteArrayOutputStream bos = QRCode.from("ldpv1;" + appInfo.getPackageName() + ";" + versionInfo.getVersionName() + ";" + patchInfo.getPatchVersion() + ";" + patchInfo.getDownloadUrl()).withSize(180, 180).stream();
            restR.getData().put("qrcodeImg", "data:image/jpeg;base64," + Base64Utils.encodeToString(bos.toByteArray()));
        }
        return new ModelAndView("patch", "restR", restR);
    }

    @RequestMapping(value = "/patch/info", method = RequestMethod.POST)
    public @ResponseBody
    RestResponse patch_info(HttpServletRequest req, Integer id, String appUid) {
        RestResponse restR = new RestResponse();
        BizAssert.notNull(id, "参数不能为空");
        PatchInfo patchInfo = patchService.findByIdAndAppUid(id, appUid);
        if (patchInfo == null) {
            throw new BizException("参数不正确");
        }
        if (patchInfo.getStatus() == PatchInfo.STATUS_UNPUBLISHED) {
            String tags = testerService.getAllTags(appUid);
            if (!StringUtils.isEmpty(tags)) {
                restR.getData().put("tags", tags + ";");
            }
        }
        AppInfo appInfo = appService.findByUid(patchInfo.getAppUid());
        VersionInfo versionInfo = appService.findVersionByUidAndVersionName(appInfo, patchInfo.getVersionName());
        if (versionInfo == null) {
            throw new BizException("该版本未找到: " + patchInfo.getVersionName());
        }
        restR.getData().put("patchInfo", patchInfo);
        restR.getData().put("successScale", patchInfo.getFormatApplyScale());
        return restR;
    }

    @RequestMapping(value = "/patch/normal_publish", method = RequestMethod.POST)
    public @ResponseBody
    RestResponse normal_publish(String appUid, Integer id) {
        RestResponse restR = new RestResponse();
        try {
            BizAssert.notNull(id, "参数不能为空");
            PatchInfo patchInfo = patchService.findByIdAndAppUid(id, appUid);
            if (patchInfo == null) {
                throw new BizException("参数不正确");
            }
            if (patchInfo.getStatus() != PatchInfo.STATUS_PUBLISHED
                    || (patchInfo.getStatus() == PatchInfo.STATUS_PUBLISHED && patchInfo.getPublishType() == PatchInfo.PUBLISH_TYPE_GRAY)) {
                patchInfo.setStatus(PatchInfo.STATUS_PUBLISHED);
                patchInfo.setPublishType(PatchInfo.PUBLISH_TYPE_NORMAL);
                patchService.updateStatus(patchInfo);
            }
        } catch (BizException e) {
            restR.setCode(-1);
            restR.setMessage(e.getMessage());
        }
        return restR;
    }

    @RequestMapping(value = "/patch/stop_publish", method = RequestMethod.POST)
    public @ResponseBody
    RestResponse stop_publish(String appUid, Integer id) {
        RestResponse restR = new RestResponse();
        try {
            BizAssert.notNull(id, "参数不能为空");
            PatchInfo patchInfo = patchService.findByIdAndAppUid(id, appUid);
            if (patchInfo == null) {
                throw new BizException("参数不正确");
            }
            if (patchInfo.getStatus() != PatchInfo.STATUS_STOPPED) {
                patchInfo.setStatus(PatchInfo.STATUS_STOPPED);
                patchService.updateStatus(patchInfo);
            }
        } catch (BizException e) {
            restR.setCode(-1);
            restR.setMessage(e.getMessage());
        }
        return restR;
    }

    @RequestMapping(value = "/patch/delete", method = RequestMethod.POST)
    public @ResponseBody
    RestResponse delete_patch(String appUid, Integer id) {
        RestResponse restR = new RestResponse();
        try {
            BizAssert.notNull(id, "应用id不能为空");
            BizAssert.notNull(id, "参数不能为空");
            PatchInfo patchInfo = patchService.findByIdAndAppUid(id, appUid);
            if (patchInfo != null) {
                if (patchInfo.getStatus() == PatchInfo.STATUS_UNPUBLISHED
                        || patchInfo.getStatus() == PatchInfo.STATUS_STOPPED) {
                    patchService.deletePatch(patchInfo);
                } else {
                    throw new BizException("已发布状态的补丁包不允许删除");
                }
            }
        } catch (BizException e) {
            restR.setCode(-1);
            restR.setMessage(e.getMessage());
        }
        return restR;
    }

    @RequestMapping(value = "/patch/gray_publish", method = RequestMethod.POST)
    public @ResponseBody
    RestResponse gray_publish(String appUid, Integer id, String tags) {
        RestResponse restR = new RestResponse();
        try {
            BizAssert.notNull(id, "参数不能为空");
            BizAssert.notEpmty(tags, "tags不能为空");

            PatchInfo patchInfo = patchService.findByIdAndAppUid(id, appUid);
            if (patchInfo == null) {
                throw new BizException("参数不正确");
            }

            if (patchInfo.getStatus() != PatchInfo.STATUS_PUBLISHED) {
                patchInfo.setStatus(PatchInfo.STATUS_PUBLISHED);
                patchInfo.setPublishType(PatchInfo.PUBLISH_TYPE_GRAY);
                patchInfo.setTags(tags);
                patchService.updateStatus(patchInfo);
            }
        } catch (BizException e) {
            restR.setCode(-1);
            restR.setMessage(e.getMessage());
        }
        return restR;
    }

    @RequestMapping(value = "/full_update", method = RequestMethod.GET)
    public ModelAndView full_update(HttpServletRequest req, String msg, String appUid, String optSuccess) {
        RestResponse restR = new RestResponse();
        BizAssert.notEpmty(appUid, "应用编号不能为空");

        AppInfo appInfo = appService.findByUid(appUid);
        BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
        FullUpdateInfo fullUpdateInfo = fullUpdateService.findByAppUid(appUid);

        if (!StringUtils.isBlank(msg)) {
            restR.setMessage(HttpRequestUtils.urlDecode(msg));
        }
        restR.getData().put("appInfo", appInfo);
        restR.getData().put("user", basicUser);
        restR.getData().put("fullUpdateInfo", fullUpdateInfo);

        if (!StringUtils.isBlank(optSuccess)) {
            restR.getData().put("optSuccess", optSuccess);
        }
        return new ModelAndView("full_update", "restR", restR);
    }

    @RequestMapping(value = "/full_update", method = RequestMethod.POST)
    public ModelAndView full_update(FullUpdateInfo fullUpdateInfo) {
        try {
            BizAssert.notEpmty(fullUpdateInfo.getAppUid(), "应用编号不能为空");
            BizAssert.notEpmty(fullUpdateInfo.getLatestVersion(), "最新版本不能为空");
            BizAssert.notEpmty(fullUpdateInfo.getDescription(), "更新说明不能为空");
            BizAssert.notEpmty(fullUpdateInfo.getDefaultUrl(), "默认下载地址不能为空");
            BizAssert.notEpmty(fullUpdateInfo.getLatestVersion(), "渠道包下载地址不能为空");

            if (fullUpdateInfo.getStatus() != 0 && fullUpdateInfo.getStatus() != 1) {
                throw new BizException("status == 0|1");
            }

            if (!fullUpdateInfo.getDefaultUrl().startsWith("http")) {
                throw new BizException("下载地址必须以http开头");
            }
            if (!fullUpdateInfo.getChannelUrl().startsWith("http")) {
                throw new BizException("下载地址必须以http开头");
            }

//            if (!StringUtils.isBlank(fullUpdateInfo.getLowestSupportVersion())) {
//                if (fullUpdateInfo.getLowestSupportVersion().compareTo(fullUpdateInfo.getLatestVersion() > 0)) {
//                    throw new BizException("最低支持版本不能比最新版本号还高");
//                }
//            }
            fullUpdateService.saveOrUpdate(fullUpdateInfo);
            return new ModelAndView("redirect:/full_update?appUid=" + fullUpdateInfo.getAppUid() + "&optSuccess=true");
        } catch (BizException e) {
            return new ModelAndView("redirect:/full_update?appUid=" + fullUpdateInfo.getAppUid() + "&msg=" + HttpRequestUtils.urlEncode(e.getMessage()));
        }
    }


    /**
     * 查询Log日志信息
     *
     * @param req
     * @return
     */
    @RequestMapping(value = "/patch/log", method = RequestMethod.GET)
    public ModelAndView patch_log(HttpServletRequest req, String appUid, String appVersion, String patchVersion, String errorCode, String model, String startDate, String endDate, String pageNum) {
        System.out.print("------>" + req.getQueryString());

        // 分页日志信息
        try {
            Integer.parseInt(pageNum);
        } catch (Throwable e) {
            pageNum = "1";
        }

        RestResponse restR = new RestResponse();
        BizAssert.notEpmty(appUid, "appUid is null");
        AppInfo appInfo = appService.findByUid(appUid);

        VersionInfo versionInfo = appService.findVersionByUidAndVersionName(appInfo, appVersion);
        if (versionInfo == null) {
            throw new BizException("该版本未找到: " + appVersion);
        }

        Map param = new HashMap();
        param.put("appUid", appUid);

        if (StringUtils.isNotEmpty(patchVersion)) {
            param.put("patchVersion", patchVersion);
        }
        if (StringUtils.isNotEmpty(appVersion)) {
            param.put("appVersion", appVersion);
        }
        if (StringUtils.isNotEmpty(errorCode)) {
            param.put("errorCode", errorCode);
        }
        if (StringUtils.isNotEmpty(model)) {
            param.put("model", model);
        }
        if (StringUtils.isNotEmpty(startDate)) {
            param.put("startTime", startDate);
        }
        if (StringUtils.isNotEmpty(endDate)) {
            param.put("endTime", endDate);
        }

        Page<PatchLog> pages = patchLogService.findByPage(param, Integer.parseInt(pageNum), 5);

        PageInfo<PatchLog> pageInfo = new PageInfo<>(pages);

        //加载所有patch信息
        List<PatchInfo> patchInfoList = patchService.findByUidAndVersionName(appUid, appVersion);
        BasicUser basicUser = (BasicUser) req.getSession().getAttribute(Constants.SESSION_LOGIN_USER);
        List<AppInfo> appInfoList = appService.findAllAppInfoByUser(basicUser);

        restR.getData().put("user", basicUser);
        restR.getData().put("appInfo", appInfo);
        restR.getData().put("patchInfoList", patchInfoList);
        restR.getData().put("appInfoList", appInfoList);
        restR.getData().put("versionInfo", versionInfo);
        restR.getData().put("versionList", appService.findAllVersion(appInfo));
        restR.getData().put("patchVersion", patchVersion != null ? patchVersion : "");
        restR.getData().put("errorCode", errorCode);
        restR.getData().put("model", model);
        restR.getData().put("startDate", startDate);
        restR.getData().put("endDate", endDate);
        restR.getData().put("maxPatchSize", maxPatchSize);
        restR.getData().put("pageInfo", pageInfo);

        logger.info(JSON.toJSONString(restR));
        return new ModelAndView("patch_log", "restR", restR);
    }
}
