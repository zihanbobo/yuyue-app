package com.yuyue.app.api.controller;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.yuyue.app.annotation.CurrentUser;
import com.yuyue.app.annotation.LoginRequired;
import com.yuyue.app.api.domain.*;
import com.yuyue.app.api.service.LoginService;
import com.yuyue.app.api.service.UploadFileService;
import com.yuyue.app.api.service.UserCommentService;
import com.yuyue.app.enums.ReturnResult;
import com.yuyue.app.utils.RedisUtil;
import com.yuyue.app.utils.ResultJSONUtils;
import com.yuyue.app.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author ly   This Controller class provides user attention, likes, comments
 */
@Slf4j
@RestController
@RequestMapping(value="/userComment", produces = "application/json; charset=UTF-8")
public class UserCommentController extends BaseController{

    @Autowired
    private UserCommentService userCommentService;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private LoginService loginService;
    @Autowired
    private UploadFileService uploadFileService;
    @Autowired
    private RedisTemplate redisTemplate;


    /**
     * 获取视频中所有的评论
     * @param videoId
     * @return
     */
    @RequestMapping("/getAllComment")
    @ResponseBody
    public JSONObject getAllComment(String videoId,String page, HttpServletRequest request, HttpServletResponse response){
        log.info("获取视频中所有的评论-------------->>/userComment/getAllComment");
        getParameterMap(request, response);
        ReturnResult returnResult =new ReturnResult();
        if(videoId.isEmpty()){
            returnResult.setMessage("视频id不能为空!!");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        Map<String,Object> map= Maps.newTreeMap();

        if (StringUtils.isEmpty(page))  page = "1";
        int limit = 10;
        int begin = (Integer.parseInt(page) - 1) * limit;
        //设置缓存
        /*
        List<UserCommentVo> userCommentList = null;
        if (redisUtil.existsKey("comment" + videoId)) {
            userCommentList = JSON.parseObject((String) redisUtil.getString("comment" + videoId),
                    new TypeReference<List<UserCommentVo>>() {});

        } else {
            userCommentList = userCommentService.getAllComment(videoId,"",begin,limit);
            String str = JSON.toJSONString(userCommentList);
            redisUtil.setString("comment" + videoId, str, 60);
        }*/
        List<UserCommentVo> userCommentList = userCommentService.getAllComment(videoId,"",begin,limit);
        if(userCommentList.isEmpty()) {
            returnResult.setMessage("暂无评论！");
        }else {
            returnResult.setMessage("返回成功！");
        }
        map.put("comment", userCommentList);
        map.put("commentNum", userCommentService.getCommentTotal(videoId));
        returnResult.setStatus(Boolean.TRUE);
        returnResult.setResult(map);
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

//    @RequestMapping("/test")
//    @ResponseBody
//    public void test(){
//        ValueOperations<String, String> valueTemplate = redisTemplate.opsForValue();
//        Gson gson = new Gson();
//
//        valueTemplate.set("StringKey1", "hello spring boot redis, String Redis");
//        String value = valueTemplate.get("StringKey1");
//        System.out.println(value);
//
//        valueTemplate.set("StringKey2", gson.toJson(new Person("theName", 11)));
//        Person person = gson.fromJson(valueTemplate.get("StringKey2"), Person.class);
//        System.out.println(person);
//    }


    /**
     * 用户添加评论
     * @param request(videoId,authorId,token)
     * @return
     */
    @RequestMapping("/addComment")
    @ResponseBody
    @LoginRequired
    public JSONObject addComment(HttpServletRequest request,@CurrentUser AppUser user, HttpServletResponse response){
        log.info("用户添加评论-------------->>/userComment/addComment");
        Map<String, String> mapValue = getParameterMap(request, response);
        ReturnResult returnResult =new ReturnResult();
        String videoId=mapValue.get("videoId");
        String authorId=mapValue.get("authorId");
        /*Map<String,Object> map= Maps.newTreeMap();*/
        if(authorId.isEmpty() || videoId.isEmpty() || user.getId().isEmpty()){
            returnResult.setMessage("作者id或视频id不能为空!!");
        } else {
            UserComment comment=new UserComment();
            String id= UUID.randomUUID().toString().replace("-","").toUpperCase();
            comment.setId(id);
            comment.setVideoId(videoId);
            comment.setUserId(user.getId());
            comment.setAuthorId(authorId);
            comment.setText(mapValue.get("text"));
            //数据插入到Comment表中
            userCommentService.addComment(comment);
            //艺人评论   用户表，视频表评论数+1
            uploadFileService.allRoleCommentAmount(authorId,videoId);
            //获取所有评论
           /* List<UserCommentVo> allComment = userCommentService.getAllComment(videoId, "",1,10);*/
            //获取评论数
            //int commentTotal = userCommentService.getCommentTotal(videoId);
            returnResult.setMessage("评论成功！！");
            returnResult.setStatus(Boolean.TRUE);
        }
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * 用户删除评论
     * @param request( 评论id ,作者id ,视频id)
     * @return
     */
    @RequestMapping("/deleteComment")
    @ResponseBody
    @LoginRequired
    public JSONObject deleteComment(HttpServletRequest request,@CurrentUser AppUser user, HttpServletResponse response){
        log.info("用户删除评论-------------->>/userComment/deleteComment");
        ReturnResult returnResult =new ReturnResult();
        Map<String, String> mapValue = getParameterMap(request, response);
        String commentId=mapValue.get("id");
/*      String videoId=mapValue.get("videoId");
        String authorId=mapValue.get("authorId");
        if(StringUtils.isEmpty(videoId) ){
            returnResult.setMessage("videoId不可为空!!");
        }else if (StringUtils.isEmpty(authorId)){
            returnResult.setMessage("authorId不可为空!!");
        }*/
        UserComment userCommentById = userCommentService.getUserCommentById(commentId);
        System.out.println(userCommentById);
        if(StringUtils.isNull(userCommentById) ){
            returnResult.setMessage("未查询到该评论!!");
        }else {
            uploadFileService.reduceCommentAmount(userCommentById.getAuthorId(),userCommentById.getVideoId());
            //通过评论id删除评论表中该评论
            userCommentService.deleteComment(commentId,userCommentById.getVideoId());

            returnResult.setMessage("删除成功！");
            returnResult.setStatus(Boolean.TRUE);
        }

        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * 查询用户所有的关注
     * @param user
     * @return
     */
    @RequestMapping("/getUserAttention")
    @ResponseBody
    @LoginRequired
    public JSONObject getUserAttention(@CurrentUser AppUser user,String content,String page ,
                                       HttpServletRequest request, HttpServletResponse response){
        log.info("查询用户所有的关注-------------->>/userComment/getUserAttention");
        getParameterMap(request, response);
        ReturnResult returnResult =new ReturnResult();
        if (StringUtils.isEmpty(page))  page = "1";
        int limit = 10;
        int begin = (Integer.parseInt(page) - 1) * limit;
        List<AppUser> appUserList= Lists.newArrayList();
//        System.out.println("---------"+user.getId());
        List<Attention> userAttentions = userCommentService.getUserAttention(user.getId(),begin,limit);

        if(userAttentions.isEmpty()){
            returnResult.setResult(userAttentions);
            returnResult.setMessage("该用户没有关注！！");
            returnResult.setStatus(Boolean.TRUE);
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        if (StringUtils.isNotEmpty(content)){
            appUserList = loginService.getAppUserMsgToLike(user.getId(), content);
            if (appUserList.isEmpty()){
                returnResult.setResult(userAttentions);
                returnResult.setMessage("查无此人！！");
                returnResult.setStatus(Boolean.TRUE);
                return ResultJSONUtils.getJSONObjectBean(returnResult);
            }
        }else {
            for (Attention attention:userAttentions
            ) {
                AppUser appUserMsg = loginService.getAppUserMsg("", "", attention.getAuthorId());
                appUserList.add(appUserMsg);
            }
        }
        returnResult.setResult(appUserList);
        returnResult.setMessage("返回成功！！");
        returnResult.setStatus(Boolean.TRUE);
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * 通过艺人id获取视频,只展示通过审核的视频
     * @param user
     * @param authorId
     * @param request
     * @return
     */
    @RequestMapping("/getVideoByAuthorId")
    @ResponseBody
    @LoginRequired
    public JSONObject getVideoByAuthorId(@CurrentUser AppUser user,String authorId,String page,
                                         HttpServletRequest request, HttpServletResponse response){
        log.info("通过艺人id获取视频-------------->>/userComment/getVideoByAuthorId");
        getParameterMap(request, response);
        ReturnResult returnResult =new ReturnResult();
        if (StringUtils.isEmpty(page))  page = "1";
        int limit = 10;
        int begin = (Integer.parseInt(page) - 1) * limit;
        if(authorId.isEmpty()  || user.getId().isEmpty()){
            returnResult.setMessage("作者id不能为空!!");
        }
        List<UploadFile> videoByAuthorId = uploadFileService.getVideoByAuthor(authorId,begin,limit);
        if(videoByAuthorId.isEmpty()){
            returnResult.setMessage("暂无视频！！");
        }else {
            returnResult.setMessage("返回成功！！");
        }
        returnResult.setStatus(Boolean.TRUE);
        returnResult.setResult(videoByAuthorId);
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * 添加关注
     * @param authorId
     * @return
     */
    @RequestMapping("/addAttention")
    @ResponseBody
    @LoginRequired
    public JSONObject addAttention(@CurrentUser AppUser user,String authorId,
                                   HttpServletRequest request, HttpServletResponse response){
        log.info("添加关注-------------->>/userComment/addAttention");
        getParameterMap(request, response);
        ReturnResult returnResult=new ReturnResult();
        if(authorId.isEmpty()  || user.getId().isEmpty()){
            returnResult.setMessage("作者id不能为空！！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }else if(authorId.equals(user.getId())){
            returnResult.setMessage("不能关注自己！！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        String attentionStatus = userCommentService.getAttentionStatus(user.getId(), authorId);
        if ("1".equals(attentionStatus)){
            returnResult.setMessage("用户已关注！！");
            returnResult.setStatus(Boolean.TRUE);
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        //用户表中的关注数据+1  ;  数据添加至Attention表中
        String id =UUID.randomUUID().toString().replace("-","").toUpperCase();
        userCommentService.addAttention(id,user.getId(),authorId);
        returnResult.setMessage("关注成功！！");
        returnResult.setStatus(Boolean.TRUE);
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * 取消用户关注
     * @param user,authorId
     * @return
     */
    @RequestMapping("/cancelAttention")
    @ResponseBody
    @LoginRequired
    public JSONObject cancelAttention(@CurrentUser AppUser user,String authorId,
                                      HttpServletRequest request, HttpServletResponse response){
        log.info("取消用户关注-------------->>/userComment/cancelAttention");
        getParameterMap(request, response);
        ReturnResult returnResult=new ReturnResult();
        if(authorId.isEmpty()  || user.getId().isEmpty()){
            returnResult.setMessage("作者id不能为空!!");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        String attentionStatus = userCommentService.getAttentionStatus(user.getId(), authorId);

        if(StringUtils.isEmpty(attentionStatus)){
            returnResult.setMessage("未关注！！");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }//关注表删除数据  ; 用户表 关注量-1
        userCommentService.cancelAttention(user.getId(),authorId);
        returnResult.setMessage("取消关注成功！！");
        returnResult.setStatus(Boolean.TRUE);
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }

    /**
     * 获取用户粉丝量
     * @param user
     * @return
     */
    @RequestMapping("/getFansSum")
    @ResponseBody
    @LoginRequired
    public JSONObject getFansSum(@CurrentUser AppUser user, HttpServletRequest request, HttpServletResponse response){
        log.info("获取用户粉丝量-------------->>/userComment/getFansSum");
        getParameterMap(request, response);
        ReturnResult returnResult =new ReturnResult();
        userCommentService.getFansSum(user.getId());
        returnResult.setMessage("返回成功！！");
        returnResult.setStatus(Boolean.TRUE);
        return ResultJSONUtils.getJSONObjectBean(returnResult);

    }


    /**
     *用户添加视频点赞
     * @param user
     * @param videoId
     * @return
     */
    @RequestMapping("/insertToLikeList")
    @ResponseBody
    @LoginRequired
    public JSONObject insertToLikeList(@CurrentUser AppUser user,String authorId,String videoId,
                                       HttpServletRequest request, HttpServletResponse response){
        log.info("用户添加视频点赞-------------->>/userComment/insertToLikeList");
        getParameterMap(request, response);
        ReturnResult returnResult =new ReturnResult();
        if(authorId.isEmpty()  ){
            returnResult.setMessage("作者id不能为空!!");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }else if (videoId.isEmpty() ){
            returnResult.setMessage("视频id不能为空!!");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        String likeStatus = userCommentService.getLikeStatus(user.getId(), videoId);
//        System.out.println("---------------------"+likeStatus);
        if(likeStatus == null || "0".equals(likeStatus)){
            //用户表及视频表中的字段LIKE_TOTAL +1;
            uploadFileService.likeAcount(authorId,videoId);
            //点赞的数据添加至LIKE表中
            userCommentService.insertToLikeList(user.getId(),authorId,videoId);
            returnResult.setMessage("点赞成功");
            returnResult.setStatus(Boolean.TRUE);
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }else {
            returnResult.setMessage("视频已点赞");
            returnResult.setStatus(Boolean.TRUE);
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
    }
    /**
     *查询用户视频点赞状态
     * @param user
     * @param videoId
     * @return
     */
    /* @RequestMapping("/getLikeStatus")
    @ResponseBody
    @LoginRequired
   public JSONObject getLikeStatus(@CurrentUser AppUser user,String videoId){
        ReturnResult returnResult =new ReturnResult();
        if( videoId.isEmpty() ){
            returnResult.setMessage("视频id不能为空!!");
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
        String likeStatus = userCommentService.getLikeStatus(user.getId(), videoId);
        if(likeStatus == null || "0".equals(likeStatus)){
            returnResult.setMessage("未点赞");
            returnResult.setStatus(Boolean.TRUE);
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }else {
            returnResult.setMessage("已点赞");
            returnResult.setStatus(Boolean.TRUE);
            return ResultJSONUtils.getJSONObjectBean(returnResult);
        }
    }
*/


    /**
     *作者查看点赞列表信息
     * @param user
     * @param
     * @return
     */
    @RequestMapping("/getLikeList")
    @ResponseBody
    @LoginRequired
    public JSONObject getLikeList(@CurrentUser AppUser user, HttpServletRequest request, HttpServletResponse response){
        log.info("作者查看点赞列表信息-------------->>/userComment/getLikeList");
        getParameterMap(request, response);
        ReturnResult returnResult =new ReturnResult();
        List<Like> likeList = userCommentService.getLikeList(user.getId());
        if(likeList.isEmpty()){
            returnResult.setMessage("暂无点赞");
        }else {
            returnResult.setResult(likeList);
            returnResult.setMessage("返回成功");
        }
        returnResult.setStatus(Boolean.TRUE);
        return ResultJSONUtils.getJSONObjectBean(returnResult);
    }
}
