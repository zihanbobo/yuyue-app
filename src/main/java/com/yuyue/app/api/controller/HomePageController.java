package com.yuyue.app.api.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.yuyue.app.api.domain.*;
import com.yuyue.app.api.service.HomePageService;
import com.yuyue.app.api.service.LoginService;
import com.yuyue.app.api.service.UploadFileService;
import com.yuyue.app.enums.ReturnResult;
import com.yuyue.app.utils.RedisUtil;
import com.yuyue.app.utils.ResultJSONUtils;
import com.yuyue.app.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * @author ly
 */
@Slf4j
@RestController
@RequestMapping(value = "/homePage", produces = "application/json; charset=UTF-8")
public class HomePageController extends BaseController {

    @Autowired
    private HomePageService homePageService;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private UploadFileService uploadFileService;
    @Autowired
    private LoginService loginService;



    /**
     * 首页展示轮播图及视频种类
     */
    @ResponseBody
    @RequestMapping("/result")
    public JSONObject homePage(HttpServletRequest request, HttpServletResponse response){
        log.info("首页展示轮播图及视频种类-------------->>/homePage/result");
        getParameterMap(request, response);
        Map<String,List> map= Maps.newHashMap();
        ReturnResult returnResult=new ReturnResult();
        List<Banner> banners=null;
        List<VideoCategory> categories=null;
        if (redisUtil.existsKey("newBanners") && redisUtil.existsKey("newCategories")){
            banners=JSON.parseObject((String)redisUtil.getString("newBanners" ),
                    new TypeReference<List<Banner>>() {});
            categories =JSON.parseObject((String)redisUtil.getString("newCategories" ),
                    new TypeReference<List<VideoCategory>>() {});
            System.out.println("------redis缓存中取出数据-------");
        } else {
            banners = homePageService.getBanner();
            redisUtil.setString("newBanners", JSON.toJSONString(banners),6000);
            categories=homePageService.getVideoCategory();
            redisUtil.setString("newCategories",JSON.toJSONString(categories));
        }
        map.put("banners",banners);
        map.put("categories",categories);
        returnResult.setResult(JSONObject.toJSON(map));
        returnResult.setMessage("返回轮播图和节目表演成功！");
        returnResult.setStatus(Boolean.TRUE);
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * 获取首页视频列表
     * @param page
     * @return
     */
    @ResponseBody
    @RequestMapping("/getVideoToHomePage")
    public JSONObject getVideoToHomePage(String page,HttpServletRequest request, HttpServletResponse response) {
        log.info("获取首页视频列表-------------->>/homePage/getVideoToHomePage");
        getParameterMap(request, response);
        Map<String,List> map= Maps.newHashMap();
        ReturnResult returnResult=new ReturnResult();

        if (StringUtils.isEmpty(page) || !page.matches("[0-9]+"))  page = "1";
        PageHelper.startPage(Integer.parseInt(page), 10);
        List<UploadFile> videoToHomePage = uploadFileService.getVideoToHomePage();
        PageInfo<UploadFile> pageInfo=new PageInfo<>(videoToHomePage);
        int pages = pageInfo.getPages();
        int currentPage = Integer.parseInt(page);
        if (pages < currentPage){

            returnResult.setMessage("暂无视频！");
            returnResult.setResult(Lists.newArrayList());
            returnResult.setStatus(Boolean.TRUE);
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }

        returnResult.setResult(videoToHomePage);
        if(CollectionUtils.isEmpty(videoToHomePage)){
            returnResult.setMessage("暂无视频！");
        } else {
            returnResult.setMessage("视频请求成功！");
        }
        returnResult.setStatus(Boolean.TRUE);
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }


    /**
     * 获取定位，省市区
     *
     * @return
     */
    @RequestMapping("/getCity")
    @ResponseBody
    public JSONObject getCity(HttpServletRequest request, HttpServletResponse response) {
        log.info("获取定位，省市区-------------->>/homePage/getCity");
        getParameterMap(request, response);
        ReturnResult returnResult=new ReturnResult();
        List<Address> list = new ArrayList<>();
        if (redisUtil.existsKey("shengshiqu")) {
            list = JSON.parseObject((String) redisUtil.getString("shengshiqu"),
                    new TypeReference<List<Address>>() {});
            log.info("缓存获取省市区");
        } else {
            list = homePageService.getAddress();
            redisUtil.setString("shengshiqu", JSON.toJSONString(list), 60*60*24*30);
        }
        returnResult.setMessage("获取成功！");
        returnResult.setStatus(Boolean.TRUE);
        returnResult.setResult(JSONArray.parseArray(JSON.toJSONString(list)));
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * 获取现场
     * @param id
     * @return
     */
    @RequestMapping("/getSite")
    @ResponseBody
    public JSONObject getSite(String page,String id,HttpServletRequest request, HttpServletResponse response) {
        log.info("获取现场节目-------------->>/homePage/getSite");
        getParameterMap(request, response);
        ReturnResult returnResult=new ReturnResult();

        if (StringUtils.isEmpty(page))  page = "1";
        int limit = 10;
        int begin = (Integer.parseInt(page) - 1) * limit;
        if (StringUtils.isEmpty(id)){
            List<YuyueSite> siteList = homePageService.getSiteList(begin,limit);
            if (StringUtils.isEmpty(siteList)){
                returnResult.setMessage("暂无信息！！");
            }else
                returnResult.setMessage("返回成功！！");
//            for (YuyueSite yuyueSite:siteList) {
//                System.out.println(yuyueSite);
//            }
            returnResult.setStatus(Boolean.TRUE);
            returnResult.setResult(siteList);
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }else {
            Map<String,Object> map=Maps.newHashMap();
            YuyueSite site = homePageService.getSite(id);
            if (StringUtils.isNull(site)){
                returnResult.setMessage("暂无信息！！");
            }else
                returnResult.setMessage("返回信息！！");
            List<SiteShow> showList = homePageService.getShow(id);
            if (StringUtils.isNull(showList))
                returnResult.setMessage("暂无节目");
            else
                returnResult.setMessage("节目表单返回成功");
            map.put("site",site);
            map.put("showList",showList);
            returnResult.setStatus(Boolean.TRUE);
            returnResult.setResult(map);
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }

    }
}
