package com.mes.carrier.facade;

import com.mes.carrier.dto.CarrierCreateDTO;
import com.mes.carrier.dto.CarrierQuery;
import com.mes.carrier.dto.CarrierUpdateDTO;
import com.mes.carrier.vo.CarrierVO;
import com.mes.common.PageResult;

/**
 * 载具对外门面。Car-2：台账；绑解见 Car-3。
 */
public interface CarrierFacade {

    CarrierVO create(CarrierCreateDTO dto);

    CarrierVO update(Long id, CarrierUpdateDTO dto);

    CarrierVO changeStatus(Long id, String toStatus, String remark);

    CarrierVO get(Long id);

    CarrierVO getByCode(String carrierCode);

    PageResult<CarrierVO> list(CarrierQuery query);
}
