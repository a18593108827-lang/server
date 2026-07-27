package com.mes.track.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.track.dto.TrackInDTO;
import com.mes.track.dto.TrackOutDTO;
import com.mes.track.dto.TrackReleaseDTO;
import com.mes.track.service.TrackService;
import com.mes.track.vo.TrackContextVO;
import com.mes.track.vo.TrackReleaseResultVO;
import com.mes.track.vo.TrackTxnResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Track 执行引擎：Release / TrackIn / TrackOut
 * <p>一期不单独暴露 Move：TrackOut 自动进入下一站 wait。
 */
@RestController
@RequestMapping("/track")
@RequiredArgsConstructor
public class TrackController {

    private final TrackService trackService;

    /** 放行：绑 active 版本快照，进首站 wait */
    @SaCheckPermission(value = {"track:release", "lot:release"}, mode = SaMode.OR)
    @OperLog(module = "Track", action = "放行")
    @PostMapping("/release")
    public R<TrackReleaseResultVO> release(@Valid @RequestBody TrackReleaseDTO dto) {
        return R.ok(trackService.release(dto.getLotId()));
    }

    /** 开工：wait → processing */
    @SaCheckPermission("track:track-in")
    @OperLog(module = "Track", action = "TrackIn")
    @PostMapping("/track-in")
    public R<TrackTxnResultVO> trackIn(@Valid @RequestBody TrackInDTO dto) {
        return R.ok(trackService.trackIn(dto.getLotId(), dto.getEqpId()));
    }

    /** 完工：processing → 下一站 wait 或 completed */
    @SaCheckPermission("track:track-out")
    @OperLog(module = "Track", action = "TrackOut")
    @PostMapping("/track-out")
    public R<TrackTxnResultVO> trackOut(@Valid @RequestBody TrackOutDTO dto) {
        return R.ok(trackService.trackOut(dto.getLotId()));
    }

    /** 执行上下文（只读） */
    @SaCheckPermission("track:view")
    @GetMapping("/lots/{lotId}/context")
    public R<TrackContextVO> context(@PathVariable Long lotId) {
        return R.ok(trackService.context(lotId));
    }
}
