package com.k4ln.debug4j.controller;


import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.error.SaErrorCode;
import cn.dev33.satoken.exception.NotHttpBasicAuthException;
import cn.dev33.satoken.secure.SaBase64Util;
import com.k4ln.debug4j.common.response.Result;
import com.k4ln.debug4j.controller.vo.*;
import com.k4ln.debug4j.service.AttachHub;
import com.k4ln.debug4j.service.AttachService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static cn.dev33.satoken.httpauth.basic.SaHttpBasicTemplate.DEFAULT_REALM;

/**
 * 增强接口类
 *
 * @author k4ln
 * @since 2024-10-22
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/attach")
public class AttachController {

    @Resource
    AttachService attachService;

    @Resource
    AttachHub attachHub;

    /**
     * 获取所有类
     *
     * @return
     */
    @PostMapping("/class")
    public Result<List<String>> getClassNames(@RequestBody @Valid AttachClassAllReqVO attachClassAllReqVO) {
        return Result.ok(attachService.getClassNames(attachClassAllReqVO));
    }

    /**
     * 获取类源码
     *
     * @param attachClassSourceReqVO
     * @return
     */
    @PostMapping("/source")
    public Result<AttachClassSourceRespVO> getClassSource(@RequestBody @Valid AttachClassSourceReqVO attachClassSourceReqVO) {
        return Result.ok(attachService.getClassSource(attachClassSourceReqVO));
    }

    /**
     * 源码热更新
     *
     * @param sourceReloadReqVO
     * @return
     */
    @PostMapping("/reload/source")
    public Result<AttachClassSourceRespVO> sourceReload(@RequestBody @Valid AttachSourceReloadReqVO sourceReloadReqVO) {
        return Result.ok(attachService.sourceReload(sourceReloadReqVO));
    }

    /**
     * 字节码热更新
     *
     * @param classFile
     * @param clientSessionId
     * @param className
     * @return
     */
    @PostMapping(value = "/reload/class", consumes = "multipart/form-data")
    public Result<AttachClassSourceRespVO> classReload(@RequestParam("file") MultipartFile classFile,
                                                       @RequestParam("clientSessionId") String clientSessionId,
                                                       @RequestParam("className") String className) {
        return Result.ok(attachService.classReload(classFile, clientSessionId, className));
    }

    /**
     * 代码还原
     *
     * @param classRestoreReqVO
     * @return
     */
    @PostMapping("/restore")
    public Result<AttachClassSourceRespVO> classRestore(@RequestBody @Valid AttachClassRestoreReqVO classRestoreReqVO) {
        return Result.ok(attachService.classRestore(classRestoreReqVO));
    }

    /**
     * 获取源码带行号
     *
     * @param sourceLineReqVO
     * @return
     */
    @PostMapping("/source/method-line")
    public Result<AttachClassSourceLineRespVO> getClassSourceMethodLine(@RequestBody @Valid AttachClassSourceLineReqVO sourceLineReqVO) {
        return Result.ok(attachService.getClassSourceMethodLine(sourceLineReqVO));
    }

    /**
     * 行代码补丁
     *
     * @param pathLineReqVO
     * @return
     */
    @PostMapping("/patch/method-line")
    public Result<AttachClassSourceLineRespVO> patchMethodLine(@RequestBody @Valid AttachClassPathLineReqVO pathLineReqVO) {
        return Result.ok(attachService.patchMethodLine(pathLineReqVO));
    }

    /**
     * 获取任务列表
     *
     * @param attachTaskReqVO
     * @return
     */
    @PostMapping("/task")
    public Result<List<AttachTaskRespVO>> getTask(@RequestBody @Valid AttachTaskReqVO attachTaskReqVO) {
        return Result.ok(attachService.getTask(attachTaskReqVO));
    }

    /**
     * 开启任务
     *
     * @param openReqVO
     * @return
     */
    @PostMapping("/task/open")
    public Result<List<AttachTaskRespVO>> openTask(@RequestBody @Valid AttachTaskOpenReqVO openReqVO) {
        return Result.ok(attachService.openTask(openReqVO));
    }

    /**
     * 关闭任务
     *
     * @param closeReqVO
     * @return
     */
    @PostMapping("/task/close")
    public Result<List<AttachTaskRespVO>> closeTask(@RequestBody @Valid AttachTaskCloseReqVO closeReqVO) {
        return Result.ok(attachService.closeTask(closeReqVO));
    }

    /**
     * 获取任务详情
     *
     * @param path
     * @return
     */
    @GetMapping("/task")
    public SseEmitter getTaskDetails(@RequestParam("path") String path, @RequestParam("sessionId") String sessionId,
                                     @RequestParam("token") String token, @RequestParam("loginId") String loginId) {
       if (SaManager.getConfig().getHttpBasic().equals(SaBase64Util.decode(token))){
           return attachHub.getSseEmitter(sessionId + "@" + path, loginId);
       } else {
           SaHolder.getResponse().setStatus(401).setHeader("WWW-Authenticate", "Basic Realm=" + DEFAULT_REALM);
           throw new NotHttpBasicAuthException().setCode(SaErrorCode.CODE_10311);
       }
    }

}
