package com.k4ln.debug4j.controller;


import com.k4ln.debug4j.common.response.Result;
import com.k4ln.debug4j.controller.vo.*;
import com.k4ln.debug4j.service.ProcessService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 进程接口类
 *
 * @author k4ln
 * @since 2024-10-22
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/process")
public class ProcessController {

    @Resource
    ProcessService processService;

    /**
     * 获取所有参数
     *
     * @return
     */
    @PostMapping("/args")
    public Result<ProcessArgRespVO> args(@RequestBody @Valid ProcessArgReqVO processArgReqVO) {
        return Result.ok(processService.args(processArgReqVO));
    }

    /**
     * 重启进程（Restart模式无法立即获取子进程参数，返回为空）
     *
     * @return
     */
    @PostMapping("/reload")
    public Result<ProcessArgRespVO> reload(@RequestBody @Valid ProcessReloadReqVO processReloadReqVO) {
        return Result.ok(processService.reload(processReloadReqVO));
    }

    /**
     * 进程内调整
     *
     * @return
     */
    @PostMapping("/adjustment")
    public Result<ProcessAdjustmentRespVO> adjustment(@RequestBody @Valid ProcessAdjustmentReqVO adjustmentReqVO) {
        return Result.ok(processService.adjustment(adjustmentReqVO));
    }

    /**
     * 进程内调整（上传文件）
     *
     * @return
     */
    @PostMapping(value = "/adjustment/upload", consumes = "multipart/form-data")
    public Result<ProcessAdjustmentRespVO> adjustmentUpload(@RequestParam("file") MultipartFile[] file,
                                                            @RequestParam("clientSessionId") String clientSessionId,
                                                            @RequestParam("fileDir") String fileDir) {
        return Result.ok(processService.adjustmentUpload(file, clientSessionId, fileDir));
    }

}
