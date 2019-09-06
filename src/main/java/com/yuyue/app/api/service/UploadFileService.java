package com.yuyue.app.api.service;
import com.alibaba.fastjson.JSONObject;
import com.yuyue.app.api.domain.AppUser;
import com.yuyue.app.api.domain.UploadFile;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public interface UploadFileService {

    UploadFile fileDetail(String userId,String videoId);

    /**
     * 根据文件名称删除文件
     *
     * @return
     */
    JSONObject deleteFile(String authorId,String id) throws Exception;

    /**
     * 上传文件到fastdfs图片服务器
     *
     * @return
     */
    JSONObject UploadFilesToServer(MultipartFile[] files,AppUser user,String fileType,String videoId)throws Exception;




    /**
     * 我的发布
     * @param id
     * @param categoryId
     * @param title
     * @param description
     * @return
     */
    JSONObject addRelease(String id, String authorId,String categoryId, String title, String description);

    /**
     * 从fastdfs服务器下载文件
     *
     * @param filesName
     * @param filesPath
     */
    void downloadFile(String filesName, String filesPath, HttpServletResponse response) throws IOException;

    /**
     * 视频点赞量
     * @param
     */
    JSONObject likeAcount(String authorId,String videoId);

    /**
     * 视频评论量
     * @param
     */
    JSONObject commentAmount(String authorId,String videoId);


    /**
     * 视频关注量
     * @param
     */
    JSONObject attentionAmount(String authorId);

    /**
     * 取消关注，关注量-1
     * @param authorId
     */
    void reduceAttentionAmount(String authorId);

    /**
     * 删除评论， 评论量 -1
     * @param authorId
     * @param videoId
     */
    void reduceCommentAmount(String authorId,String videoId);
    /**
     * 默认每张表查询五表记录
     * @param bdgin
     * @param size
     * @return
     */
    List<UploadFile> getVideo(String tableName, int bdgin, int size,String categoryId);



    /**
     * 我的发布
     * @param categoryId
     * @param title
     * @param description
     * @return
     */
    JSONObject addRelease(String authorId,String categoryId, String title, String description, String fileType, String vedioAddress,String fileName,String filesPath);

    /**
     *通过作者id 获取视频
     * @param
     * @return
     */
     List<UploadFile> getVideoByAuthorId(String authorId);
    /**
     *用户通过作者id查询本人上传的视频,只展示通过审核的视频
     * @param authorId
     * @return
     */
    List<UploadFile> getVideoByAuthor(String authorId);


}
