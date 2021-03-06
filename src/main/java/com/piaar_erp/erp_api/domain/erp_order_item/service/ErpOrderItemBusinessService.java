package com.piaar_erp.erp_api.domain.erp_order_item.service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.piaar_erp.erp_api.domain.erp_first_merge_header.dto.ErpFirstMergeHeaderDto;
import com.piaar_erp.erp_api.domain.erp_first_merge_header.entity.ErpFirstMergeHeaderEntity;
import com.piaar_erp.erp_api.domain.erp_first_merge_header.service.ErpFirstMergeHeaderService;
import com.piaar_erp.erp_api.domain.erp_order_item.dto.ErpOrderItemDto;
import com.piaar_erp.erp_api.domain.erp_order_item.entity.ErpOrderItemEntity;
import com.piaar_erp.erp_api.domain.erp_order_item.proj.ErpOrderItemProj;
import com.piaar_erp.erp_api.domain.erp_order_item.vo.ErpOrderItemVo;
import com.piaar_erp.erp_api.domain.erp_second_merge_header.dto.DetailDto;
import com.piaar_erp.erp_api.domain.erp_second_merge_header.dto.ErpSecondMergeHeaderDto;
import com.piaar_erp.erp_api.domain.erp_second_merge_header.entity.ErpSecondMergeHeaderEntity;
import com.piaar_erp.erp_api.domain.erp_second_merge_header.service.ErpSecondMergeHeaderService;
import com.piaar_erp.erp_api.domain.excel_form.waybill.WaybillExcelFormDto;
import com.piaar_erp.erp_api.domain.excel_form.waybill.WaybillExcelFormManager;
import com.piaar_erp.erp_api.domain.exception.CustomExcelFileUploadException;
import com.piaar_erp.erp_api.domain.product_option.dto.ProductOptionDto;
import com.piaar_erp.erp_api.domain.product_option.entity.ProductOptionEntity;
import com.piaar_erp.erp_api.domain.product_option.service.ProductOptionService;
import com.piaar_erp.erp_api.domain.release_stock.entity.ReleaseStockEntity;
import com.piaar_erp.erp_api.domain.release_stock.service.ReleaseStockService;
import com.piaar_erp.erp_api.utils.CustomDateUtils;
import com.piaar_erp.erp_api.utils.CustomExcelUtils;
import com.piaar_erp.erp_api.utils.CustomFieldUtils;
import com.piaar_erp.erp_api.utils.CustomUniqueKeyUtils;

import org.apache.commons.io.FilenameUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ErpOrderItemBusinessService {
    private final ErpOrderItemService erpOrderItemService;
    private final ProductOptionService productOptionService;
    private final ErpFirstMergeHeaderService erpFirstMergeHeaderService;
    private final ErpSecondMergeHeaderService erpSecondMergeHeaderService;
    private final ReleaseStockService releaseStockService;

    // Excel file extension.
    private final List<String> EXTENSIONS_EXCEL = Arrays.asList("xlsx", "xls");

    private final Integer PIAAR_ERP_ORDER_ITEM_SIZE = 34;
    private final Integer PIAAR_ERP_ORDER_MEMO_START_INDEX = 24;

    private final List<String> PIAAR_ERP_ORDER_HEADER_NAME_LIST = Arrays.asList(
            "????????? ????????????",
            "?????????",
            "????????????",
            "??????",
            "????????????",
            "????????????1",
            "????????????2",
            "??????",
            "????????????",
            "???????????? ????????????1",
            "???????????? ????????????2",
            "???????????? ????????????",
            "???????????? ????????????",
            "????????????",
            "?????????",
            "????????????",
            "???????????????",
            "???????????????",
            "????????????",
            "?????????",
            "?????????",
            "????????? ????????????",
            "????????? ????????????",
            "?????? ????????????",
            "????????????1",
            "????????????2",
            "????????????3",
            "????????????4",
            "????????????5",
            "????????????6",
            "????????????7",
            "????????????8",
            "????????????9",
            "????????????10");

    /**
     * <b>Extension Check</b>
     * <p>
     *
     * @param file : MultipartFile
     * @throws CustomExcelFileUploadException
     */
    public void isExcelFile(MultipartFile file) {
        String extension = FilenameUtils.getExtension(file.getOriginalFilename().toLowerCase());

        if (EXTENSIONS_EXCEL.contains(extension)) {
            return;
        }
        throw new CustomExcelFileUploadException("This is not an excel file.");
    }

    /**
     * <b>Upload Excel File</b>
     * <p>
     * ????????? ?????? ????????? ???????????????.
     *
     * @param file : MultipartFile
     * @return List::ErpOrderItemVo::
     * @throws CustomExcelFileUploadException
     * @see ErpOrderItemBusinessService#getErpOrderItemForm
     */
    public List<ErpOrderItemVo> uploadErpOrderExcel(MultipartFile file) {
        Workbook workbook = null;
        try {
            workbook = WorkbookFactory.create(file.getInputStream());
        } catch (IOException e) {
            throw new CustomExcelFileUploadException("????????? ????????? ?????? ????????? ????????????.\n????????? ?????? ????????? ?????????????????????.");
        }

        Sheet sheet = workbook.getSheetAt(0);

        List<ErpOrderItemVo> vos = new ArrayList<>();
        try {
            vos = this.getErpOrderItemForm(sheet);
        } catch (NullPointerException e) {
            throw new CustomExcelFileUploadException("?????? ?????? ???????????? ???????????? ?????? ?????? ???????????????.");
        } catch (IllegalStateException e) {
            throw new CustomExcelFileUploadException("????????? ?????? ????????? ????????? ????????? ?????? ?????? ???????????????.\n????????? ?????? ????????? ?????????????????????.");
        } catch (IllegalArgumentException e) {
            throw new CustomExcelFileUploadException("????????? ????????? ?????? ????????? ????????????.\n????????? ?????? ????????? ?????????????????????.");
        }

        return vos;
    }

    private List<ErpOrderItemVo> getErpOrderItemForm(Sheet worksheet) {
        List<ErpOrderItemVo> itemVos = new ArrayList<>();

        Row firstRow = worksheet.getRow(0);
        // ????????? ?????? ?????? ??????
        for (int i = 0; i < PIAAR_ERP_ORDER_ITEM_SIZE; i++) {
            Cell cell = firstRow.getCell(i);
            String headerName = cell != null ? cell.getStringCellValue() : null;
            // ????????? ????????? ????????????
            if (!PIAAR_ERP_ORDER_HEADER_NAME_LIST.get(i).equals(headerName)) {
                throw new CustomExcelFileUploadException("????????? ????????? ?????? ????????? ????????????.\n????????? ?????? ????????? ?????????????????????.");
            }
        }

        for (int i = 1; i < worksheet.getPhysicalNumberOfRows(); i++) {
            Row row = worksheet.getRow(i);
            if (row == null)
                break;

            Object cellValue = new Object();
            List<String> customManagementMemo = new ArrayList<>();

            // type check and data setting of managementMemo1~10.
            for (int j = PIAAR_ERP_ORDER_MEMO_START_INDEX; j < PIAAR_ERP_ORDER_ITEM_SIZE; j++) {
                Cell cell = row.getCell(j);

                if (cell == null || cell.getCellType().equals(CellType.BLANK)) {
                    cellValue = "";
                } else if (cell.getCellType().equals(CellType.NUMERIC)) {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        Instant instant = Instant.ofEpochMilli(cell.getDateCellValue().getTime());
                        LocalDateTime date = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
                        // yyyy-MM-dd'T'HH:mm:ss -> yyyy-MM-dd HH:mm:ss??? ??????
                        String newDate = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        cellValue = newDate;
                    } else {
                        cellValue = cell.getNumericCellValue();
                    }
                } else {
                    cellValue = cell.getStringCellValue();
                }
                customManagementMemo.add(cellValue.toString());
            }

            // price, deliveryCharge - ?????? ?????? string, number ??????
            String priceStr = (row.getCell(18) == null) ? "0" : (row.getCell(18).getCellType().equals(CellType.NUMERIC) ?
                    Integer.toString((int) row.getCell(18).getNumericCellValue()) : row.getCell(18).getStringCellValue());

            String deliveryChargeStr = (row.getCell(19) == null) ? "0" : (row.getCell(19).getCellType().equals(CellType.NUMERIC) ?
                    Integer.toString((int) row.getCell(19).getNumericCellValue()) : row.getCell(19).getStringCellValue());

            // '?????? ????????????' ?????? ???????????? ???????????? '????????? ????????????'??? ????????????
            String releaseOptionCode = (row.getCell(23) != null) ? row.getCell(23).getStringCellValue() : (row.getCell(22) == null ? "" : row.getCell(22).getStringCellValue());

            ErpOrderItemVo excelVo = ErpOrderItemVo.builder()
                    .uniqueCode(null)
                    .prodName(row.getCell(1) != null ? row.getCell(1).getStringCellValue() : "")
                    .optionName(row.getCell(2) != null ? row.getCell(2).getStringCellValue() : "")
                    .unit(row.getCell(3) != null ? Integer.toString((int) row.getCell(3).getNumericCellValue()) : "")
                    .receiver(row.getCell(4) != null ? row.getCell(4).getStringCellValue() : "")
                    .receiverContact1(row.getCell(5) != null ? row.getCell(5).getStringCellValue() : "")
                    .receiverContact2(row.getCell(6) != null ? row.getCell(6).getStringCellValue() : "")
                    .destination(row.getCell(7) != null ? row.getCell(7).getStringCellValue() : "")
                    .salesChannel(row.getCell(8) != null ? row.getCell(8).getStringCellValue() : "")
                    .orderNumber1(row.getCell(9) != null ? row.getCell(9).getStringCellValue() : "")
                    .orderNumber2(row.getCell(10) != null ? row.getCell(10).getStringCellValue() : "")
                    .channelProdCode(row.getCell(11) != null ? row.getCell(11).getStringCellValue() : "")
                    .channelOptionCode(row.getCell(12) != null ? row.getCell(12).getStringCellValue() : "")
                    .zipCode(row.getCell(13) != null ? row.getCell(13).getStringCellValue() : "")
                    .courier(row.getCell(14) != null ? row.getCell(14).getStringCellValue() : "")
                    .transportType(row.getCell(15) != null ? row.getCell(15).getStringCellValue() : "")
                    .deliveryMessage(row.getCell(16) != null ? row.getCell(16).getStringCellValue() : "")
                    .waybillNumber(row.getCell(17) != null ? row.getCell(17).getStringCellValue() : "")
                    .price(priceStr)
                    .deliveryCharge(deliveryChargeStr)
                    .barcode(row.getCell(20) != null ? row.getCell(20).getStringCellValue() : "")
                    .prodCode(row.getCell(21) != null ? row.getCell(21).getStringCellValue() : "")
                    .optionCode(row.getCell(22) != null ? row.getCell(22).getStringCellValue() : "")
                    .releaseOptionCode(releaseOptionCode)
                    .managementMemo1(customManagementMemo.get(0))
                    .managementMemo2(customManagementMemo.get(1))
                    .managementMemo3(customManagementMemo.get(2))
                    .managementMemo4(customManagementMemo.get(3))
                    .managementMemo5(customManagementMemo.get(4))
                    .managementMemo6(customManagementMemo.get(5))
                    .managementMemo7(customManagementMemo.get(6))
                    .managementMemo8(customManagementMemo.get(7))
                    .managementMemo9(customManagementMemo.get(8))
                    .managementMemo10(customManagementMemo.get(9))
                    .freightCode(null)
                    .build();

            itemVos.add(excelVo);
        }
        return itemVos;
    }

    public void createBatch(List<ErpOrderItemDto> orderItemDtos) {
        UUID USER_ID = UUID.randomUUID();
        List<ErpOrderItemDto> newOrderItemDtos = this.itemDuplicationCheck(orderItemDtos);

        List<ErpOrderItemEntity> orderItemEntities = newOrderItemDtos.stream()
                .map(r -> {
                    r.setId(UUID.randomUUID())
                            .setUniqueCode(CustomUniqueKeyUtils.generateKey())
                            .setFreightCode(CustomUniqueKeyUtils.generateFreightCode())
                            .setSalesYn("n")
                            .setReleaseOptionCode(r.getOptionCode())
                            .setReleaseYn("n")
                            .setStockReflectYn("n")
                            .setCreatedAt(CustomDateUtils.getCurrentDateTime())
                            .setCreatedBy(USER_ID);

                    return ErpOrderItemEntity.toEntity(r);
                }).collect(Collectors.toList());

//        erpOrderItemService.saveListAndModify(orderItemEntities);
        erpOrderItemService.bulkInsert(orderItemEntities);
    }

    public List<ErpOrderItemDto> itemDuplicationCheck(List<ErpOrderItemDto> dtos) {
        List<ErpOrderItemDto> newItems = dtos.stream().filter(r -> r.getOrderNumber1().isEmpty()).collect(Collectors.toList());
        List<ErpOrderItemDto> duplicationCheckItems = dtos.stream().filter(r -> !r.getOrderNumber1().isEmpty()).collect(Collectors.toList());

        List<String> orderNumber1 = new ArrayList<>();
        List<String> receiver = new ArrayList<>();
        List<String> prodName = new ArrayList<>();
        List<String> optionName = new ArrayList<>();
        List<Integer> unit = new ArrayList<>();
        duplicationCheckItems.stream().forEach(r -> {
            orderNumber1.add(r.getOrderNumber1());
            receiver.add(r.getReceiver());
            prodName.add(r.getProdName());
            optionName.add(r.getOptionName());
            unit.add(r.getUnit());
        });

        List<ErpOrderItemEntity> duplicationEntities = erpOrderItemService.findDuplicationItems(orderNumber1, receiver, prodName, optionName, unit);

        if (duplicationEntities.size() == 0) {
            return dtos;
        } else {
            for (int i = 0; i < duplicationCheckItems.size(); i++) {
                boolean duplication = false;
                // ???????????? + ????????? + ????????? + ????????? + ?????? ??? ??????????????? ?????? ??????
                for (int j = 0; j < duplicationEntities.size(); j++) {
                    if (duplicationEntities.get(j).getOrderNumber1().equals(duplicationCheckItems.get(i).getOrderNumber1())
                            && duplicationEntities.get(j).getReceiver().equals(duplicationCheckItems.get(i).getReceiver())
                            && duplicationEntities.get(j).getProdName().equals(duplicationCheckItems.get(i).getProdName())
                            && duplicationEntities.get(j).getOptionName().equals(duplicationCheckItems.get(i).getOptionName())
                            && duplicationEntities.get(j).getUnit().equals(duplicationCheckItems.get(i).getUnit())) {
                        duplication = true;
                        break;
                    }
                }
                if (!duplication) {
                    newItems.add(duplicationCheckItems.get(i));
                }
            }
        }
        return newItems;
    }

    /**
     * <b>DB Select Related Method</b>
     * <p>
     * ????????? ???????????? ????????? ?????? ????????????.
     * ????????? ??????????????? ???????????? ??????????????? ?????? Dto??? ????????????.
     *
     * @param params : Map::String, Object::
     * @return List::ErpOrderItemVo::
     * @see ErpOrderItemService#findAllM2OJ
     * @see ErpOrderItemBusinessService#setOptionStockUnit
     */
    public List<ErpOrderItemVo> searchBatch(Map<String, Object> params) {
        // ????????? ?????? ?????? ???????????? ????????????
        List<ErpOrderItemProj> itemProjs = erpOrderItemService.findAllM2OJ(params);       // ????????? ?????? x
        // ?????????????????? ??????
        List<ErpOrderItemVo> ErpOrderItemVos = this.setOptionStockUnit(itemProjs);
        return ErpOrderItemVos;
    }

    public List<ErpOrderItemVo> searchBatchByIds(List<UUID> ids, Map<String, Object> params) {

        // ????????? ?????? ?????? ???????????? ????????????
        List<ErpOrderItemProj> itemProjs = erpOrderItemService.findAllM2OJ(ids, params);       // ????????? ?????? x
        // ?????????????????? ??????
        List<ErpOrderItemVo> ErpOrderItemVos = this.setOptionStockUnit(itemProjs);
        return ErpOrderItemVos;
    }

    /**
     * <b>DB Select Related Method</b>
     * <p>
     * ????????? ???????????? ????????? ?????? ????????????.
     * ????????? ??????????????? ???????????? ??????????????? ?????? Dto??? ????????????.
     *
     * @param params   : Map::String, Object::
     * @param pageable : Pageable
     * @return List::ErpOrderItemVo::
     * @see ErpOrderItemService#findAllM2OJ
     * @see ErpOrderItemBusinessService#setOptionStockUnit
     */
    public Page<ErpOrderItemVo> searchBatchByPaging(Map<String, Object> params, Pageable pageable) {
        Page<ErpOrderItemProj> itemPages = erpOrderItemService.findAllM2OJByPage(params, pageable);
        // ????????? ?????? ?????? ???????????? ????????????
        List<ErpOrderItemProj> itemProjs = itemPages.getContent();    // ????????? ?????? o
        // ?????????????????? ??????
        List<ErpOrderItemVo> ErpOrderItemVos = this.setOptionStockUnit(itemProjs);

        return new PageImpl(ErpOrderItemVos, pageable, itemPages.getTotalElements());
    }

    public Page<ErpOrderItemVo> searchReleaseItemBatchByPaging(Map<String, Object> params, Pageable pageable) {
        Page<ErpOrderItemProj> itemPages = erpOrderItemService.findReleaseItemM2OJByPage(params, pageable);
        // ????????? ?????? ?????? ???????????? ????????????
        List<ErpOrderItemProj> itemProjs = itemPages.getContent();    // ????????? ?????? o
        // ?????????????????? ??????
        List<ErpOrderItemVo> ErpOrderItemVos = this.setOptionStockUnit(itemProjs);

        return new PageImpl(ErpOrderItemVos, pageable, itemPages.getTotalElements());
    }

    /**
     * <b>DB Select Related Method</b>
     * <p>
     * ????????? ????????? ?????? ??????????????? ???????????? ?????? ???????????? ?????????????????? ????????????.
     * ?????????????????? ??????????????? ????????? ?????? ???????????? ????????????.
     *
     * @param itemProjs : List::ErpOrderItemVo::
     * @return List::ErpOrderItemVo::
     * @see ProductOptionService#searchStockUnit
     * @see ErpOrderItemVo#toVo
     */
    public List<ErpOrderItemVo> setOptionStockUnit(List<ErpOrderItemProj> itemProjs) {
        // ????????? ???????????? ??????????????? 
        List<ProductOptionEntity> optionEntities = itemProjs.stream().filter(r -> r.getProductOption() != null ? true : false).collect(Collectors.toList())
                .stream().map(r -> r.getProductOption()).collect(Collectors.toList());

        List<ProductOptionDto> optionDtos = productOptionService.searchStockUnit(optionEntities);
        List<ErpOrderItemVo> itemVos = itemProjs.stream().map(r -> ErpOrderItemVo.toVo(r)).collect(Collectors.toList());

        // ?????? ??????????????? StockSumUnit(??? ?????? ?????? - ??? ?????? ??????)?????? ??????
        List<ErpOrderItemVo> erpOrderItemVos = itemVos.stream().map(itemVo -> {
            // ?????? ????????? ????????? ????????? ??????????????? ????????????
            optionDtos.stream().forEach(option -> {
                if (itemVo.getOptionCode().equals(option.getCode())) {
                    itemVo.setOptionStockUnit(option.getStockSumUnit().toString());
                }
            });
            return itemVo;
        }).collect(Collectors.toList());

        return erpOrderItemVos;
    }

    /**
     * <b>DB Update Related Method</b>
     * <p>
     * ?????? ???????????? salesYn(?????? ??????)??? ??????????????????.
     *
     * @param itemDtos : List::ErpOrderItemDto::
     * @see ErpOrderItemService#findAllByIdList
     * @see CustomDateUtils#getCurrentDateTime
     * @see ErpOrderItemService#saveListAndModify
     */
    public void changeBatchForSalesYn(List<ErpOrderItemDto> itemDtos) {
        List<UUID> idList = itemDtos.stream().map(dto -> dto.getId()).collect(Collectors.toList());
        List<ErpOrderItemEntity> entities = erpOrderItemService.findAllByIdList(idList);

        entities.forEach(entity -> {
            itemDtos.forEach(dto -> {
                if (entity.getId().equals(dto.getId())) {
                    entity.setSalesYn(dto.getSalesYn()).setSalesAt(dto.getSalesAt());
                }
            });
        });

        erpOrderItemService.saveListAndModify(entities);
    }

    /**
     * <b>DB Update Related Method</b>
     * <p>
     * ?????? ???????????? releaseYn(?????? ??????)??? ??????????????????.
     *
     * @param itemDtos : List::ErpOrderItemDto::
     * @see ErpOrderItemService#findAllByIdList
     * @see CustomDateUtils#getCurrentDateTime
     * @see ErpOrderItemService#saveListAndModify
     */
    public void changeBatchForReleaseYn(List<ErpOrderItemDto> itemDtos) {
        List<UUID> idList = itemDtos.stream().map(dto -> dto.getId()).collect(Collectors.toList());
        List<ErpOrderItemEntity> entities = erpOrderItemService.findAllByIdList(idList);

        entities.forEach(entity -> {
            itemDtos.forEach(dto -> {
                if (entity.getId().equals(dto.getId())) {
                    entity.setReleaseYn(dto.getReleaseYn()).setReleaseAt(dto.getReleaseAt());
                }
            });
        });

        erpOrderItemService.saveListAndModify(entities);
    }

    /**
     * <b>Data Delete Related Method</b>
     * <p>
     * ????????? ?????? ???????????? ????????????.
     *
     * @param itemDtos : List::ErpOrderItemDto::
     * @see ErpOrderItemEntity#toEntity
     * @see ErpOrderItemService#delete
     */
    public void deleteBatch(List<ErpOrderItemDto> itemDtos) {
        List<UUID> itemId = itemDtos.stream().map(r -> r.getId()).collect(Collectors.toList());
        erpOrderItemService.deleteBatch(itemId);
    }

    /**
     * <b>Data Update Related Method</b>
     * <p>
     * ????????? ?????? ???????????? ????????????.
     *
     * @param dto : ErpOrderItemDto
     * @see ErpOrderItemService#searchOne
     * @see ErpOrderItemService#saveAndModify
     */
    public void updateOne(ErpOrderItemDto dto) {
        ErpOrderItemEntity entity = erpOrderItemService.searchOne(dto.getId());

        entity.setProdName(dto.getProdName()).setOptionName(dto.getOptionName())
                .setUnit(dto.getUnit()).setReceiver(dto.getReceiver()).setReceiverContact1(dto.getReceiverContact1())
                .setReceiverContact2(dto.getReceiverContact2())
                .setDestination(dto.getDestination())
                .setSalesChannel(dto.getSalesChannel())
                .setOrderNumber1(dto.getOrderNumber1())
                .setOrderNumber2(dto.getOrderNumber2())
                .setChannelProdCode(dto.getChannelProdCode())
                .setChannelOptionCode(dto.getChannelOptionCode())
                .setZipCode(dto.getZipCode())
                .setCourier(dto.getCourier())
                .setTransportType(dto.getTransportType())
                .setDeliveryMessage(dto.getDeliveryMessage())
                .setWaybillNumber(dto.getWaybillNumber())
                .setPrice(dto.getPrice())
                .setDeliveryCharge(dto.getDeliveryCharge())
                .setBarcode(dto.getBarcode())
                .setProdCode(dto.getProdCode())
                .setOptionCode(dto.getOptionCode())
                .setReleaseOptionCode(dto.getReleaseOptionCode())
                .setManagementMemo1(dto.getManagementMemo1())
                .setManagementMemo2(dto.getManagementMemo2())
                .setManagementMemo3(dto.getManagementMemo3())
                .setManagementMemo4(dto.getManagementMemo4())
                .setManagementMemo5(dto.getManagementMemo5())
                .setManagementMemo6(dto.getManagementMemo6())
                .setManagementMemo7(dto.getManagementMemo7())
                .setManagementMemo8(dto.getManagementMemo8())
                .setManagementMemo9(dto.getManagementMemo9())
                .setManagementMemo10(dto.getManagementMemo10());

        erpOrderItemService.saveAndModify(entity);
    }

    /**
     * <b>Data Update Related Method</b>
     * <p>
     * ?????? ?????? ??????????????? ????????? ?????? ??????????????? ?????? ??????????????? ????????????.
     *
     * @param itemDtos : List::ErpOrderItemDto::
     * @see ErpOrderItemService#findAllByIdList
     * @see ErpOrderItemService#saveListAndModify
     */
    @Transactional
    public void changeBatchForAllOptionCode(List<ErpOrderItemDto> itemDtos) {
        List<ErpOrderItemEntity> entities = erpOrderItemService.getEntities(itemDtos);

        entities.forEach(entity -> {
            itemDtos.forEach(dto -> {
                if (entity.getId().equals(dto.getId())) {
                    entity.setOptionCode(dto.getOptionCode())
                            .setReleaseOptionCode(dto.getOptionCode());
                }
            });
        });

//        erpOrderItemService.saveListAndModify(entities);
    }

    /**
     * <b>Data Update Related Method</b>
     * <p>
     * ?????? ??????????????? ????????????.
     *
     * @param itemDtos : List::ErpOrderItemDto::
     * @see ErpOrderItemService#findAllByIdList
     * @see ErpOrderItemService#saveListAndModify
     */
    public void changeBatchForReleaseOptionCode(List<ErpOrderItemDto> itemDtos) {
        List<UUID> idList = itemDtos.stream().map(r -> r.getId()).collect(Collectors.toList());
        List<ErpOrderItemEntity> entities = erpOrderItemService.findAllByIdList(idList);

        entities.stream().forEach(entity -> {
            itemDtos.stream().forEach(dto -> {
                if (entity.getId().equals(dto.getId())) {
                    entity.setReleaseOptionCode(dto.getReleaseOptionCode());
                }
            });
        });
        erpOrderItemService.saveListAndModify(entities);
    }

    /**
     * <b>Data Processing Related Method</b>
     * <p>
     * ????????? > ????????? ???????????? > ?????? > ????????? > ????????? ????????? ????????????
     * ?????? ??????????????? + ?????? ????????? ??????????????? ????????? ?????????
     * ?????? ???????????? ?????? ????????? ????????? ????????? ???????????? ???????????? ????????????
     *
     * @param firstMergeHeaderId : UUID
     * @param dtos               : List::ErpOrderItemDto::
     * @return List::ErpOrderItemVo::
     * @see ErpOrderItemBusinessService#searchErpFirstMergeHeader
     * @see CustomFieldUtils#getFieldValue
     * @see CustomFieldUtils#setFieldValue
     */
    public List<ErpOrderItemVo> getFirstMergeItem(UUID firstMergeHeaderId, List<ErpOrderItemDto> dtos) {
        List<ErpOrderItemVo> itemVos = dtos.stream().map(r -> ErpOrderItemVo.toVo(r)).collect(Collectors.toList());

        // ????????? ?????? ??????????????? ??????
        ErpFirstMergeHeaderDto headerDto = this.searchErpFirstMergeHeader(firstMergeHeaderId);

        // ?????? ????????? ??????
        List<String> matchedColumnName = headerDto.getHeaderDetail().getDetails().stream().filter(r -> r.getMergeYn().equals("y")).collect(Collectors.toList())
                .stream().map(r -> r.getMatchedColumnName()).collect(Collectors.toList());

        // fixedValue??? ???????????? ????????? ???????????? fixedValue??? ??????
        Map<String, String> fixedValueMap = headerDto.getHeaderDetail().getDetails().stream().filter(r -> !r.getFixedValue().isBlank()).collect(Collectors.toList())
                .stream().collect(Collectors.toMap(
                        key -> key.getMatchedColumnName(),
                        value -> value.getFixedValue()
                ));

        itemVos.sort(Comparator.comparing(ErpOrderItemVo::getReceiver)
                .thenComparing(ErpOrderItemVo::getReceiverContact1)
                .thenComparing(ErpOrderItemVo::getDestination)
                .thenComparing(ErpOrderItemVo::getProdName)
                .thenComparing(ErpOrderItemVo::getOptionName));


        // ????????? ?????? ?????????
        List<ErpOrderItemVo> mergeItemVos = new ArrayList<>();

        Set<String> deliverySet = new HashSet<>();
        for (int i = 0; i < itemVos.size(); i++) {
            StringBuilder sb = new StringBuilder();
            sb.append(itemVos.get(i).getReceiver());
            sb.append(itemVos.get(i).getReceiverContact1());
            sb.append(itemVos.get(i).getDestination());
            sb.append(itemVos.get(i).getProdName());
            sb.append(itemVos.get(i).getOptionName());

            String resultStr = sb.toString();

            mergeItemVos.add(itemVos.get(i));
            int currentMergeItemIndex = mergeItemVos.size() - 1;

            // ???????????????(?????? + ??????)
            if (!deliverySet.add(resultStr)) {
                ErpOrderItemVo currentVo = mergeItemVos.get(currentMergeItemIndex);
                ErpOrderItemVo prevVo = mergeItemVos.get(currentMergeItemIndex - 1);

                // ?????? ?????????
                int sumUnit = Integer.parseInt(prevVo.getUnit()) + Integer.parseInt(currentVo.getUnit());
                CustomFieldUtils.setFieldValue(prevVo, "unit", String.valueOf(sumUnit));

                // ???????????? ?????? ????????? ?????? - ????????? ????????????
                matchedColumnName.forEach(columnName -> {
                    if (!columnName.equals("unit")) {
                        String prevFieldValue = CustomFieldUtils.getFieldValue(prevVo, columnName) == null ? "" : CustomFieldUtils.getFieldValue(prevVo, columnName);
                        String currentFieldValue = CustomFieldUtils.getFieldValue(currentVo, columnName) == null ? "" : CustomFieldUtils.getFieldValue(currentVo, columnName);
                        CustomFieldUtils.setFieldValue(prevVo, columnName, prevFieldValue + "|&&|" + currentFieldValue);
                    }
                });

                // ??????????????? ??????
                mergeItemVos.remove(currentMergeItemIndex);
            }

            // fixedValue??? ????????? column?????? fixedValue????????? ???????????? ???????????????
            fixedValueMap.entrySet().stream().forEach(map -> {
                CustomFieldUtils.setFieldValue(mergeItemVos.get(mergeItemVos.size() - 1), map.getKey(), map.getValue());
            });
        }
        return mergeItemVos;
    }

    /**
     * <b>Data Select Related Method</b>
     * <p>
     * firstMergeHeaderId??? ???????????? 1??? ???????????? ???????????? ????????????.
     *
     * @param firstMergeHeaderId : UUID
     * @return ErpFirstMergeHeaderDto
     * @see ErpFirstMergeHeaderService#searchOne
     * @see ErpFirstMergeHeaderDto#toDto
     */
    public ErpFirstMergeHeaderDto searchErpFirstMergeHeader(UUID firstMergeHeaderId) {
        ErpFirstMergeHeaderEntity firstMergeHeaderEntity = erpFirstMergeHeaderService.searchOne(firstMergeHeaderId);
        return ErpFirstMergeHeaderDto.toDto(firstMergeHeaderEntity);
    }

    /**
     * <b>Data Processing Related Method</b>
     * <p>
     * ?????? ????????? splitter??? ????????? ????????? ???????????? ????????? ???????????? ????????????
     * ?????? ????????????????????? ?????????(|&&|)??? ????????? ????????????
     * ????????? ????????? ???????????? ???????????? ??????????????? ???????????????
     *
     * @param secondMergeHeaderId : UUID
     * @param dtos                : List::ErpOrderItemDto::
     * @return List::ErpOrderItemVo::
     * @see ErpOrderItemBusinessService#searchErpSecondMergeHeader
     * @see CustomFieldUtils#getFieldValue
     * @see CustomFieldUtils#setFieldValue
     */
    public List<ErpOrderItemVo> getSecondMergeItem(UUID secondMergeHeaderId, List<ErpOrderItemDto> dtos) {
        List<ErpOrderItemVo> itemVos = dtos.stream().map(r -> ErpOrderItemVo.toVo(r)).collect(Collectors.toList());

        // ????????? ?????? ??????????????? ??????
        ErpSecondMergeHeaderDto headerDto = this.searchErpSecondMergeHeader(secondMergeHeaderId);

        Map<String, String> splitterMap = headerDto.getHeaderDetail().getDetails().stream().filter(r -> r.getMergeYn().equals("y")).collect(Collectors.toList())
                .stream().collect(Collectors.toMap(
                        r -> r.getMatchedColumnName(),
                        r -> r.getSplitter()
                ));

        // fixedValue??? ???????????? ????????? ???????????? fixedValue??? ??????
        Map<String, String> fixedValueMap = headerDto.getHeaderDetail().getDetails().stream().filter(r -> !r.getFixedValue().isBlank()).collect(Collectors.toList())
                .stream().collect(Collectors.toMap(
                        r -> r.getMatchedColumnName(),
                        r -> r.getFixedValue()));

        itemVos.sort(Comparator.comparing(ErpOrderItemVo::getReceiver)
                .thenComparing(ErpOrderItemVo::getReceiverContact1)
                .thenComparing(ErpOrderItemVo::getDestination)
                .thenComparing(ErpOrderItemVo::getProdName)
                .thenComparing(ErpOrderItemVo::getOptionName));

        for (int i = 0; i < itemVos.size() && i < dtos.size(); i++) {
            ErpOrderItemVo currentVo = itemVos.get(i);
            ErpOrderItemDto originDto = dtos.get(i);

            // 1. splitter??? ????????? ????????? ????????? ?????? ???????????? ?????? ???????????? ??? ????????? ????????? ?????? ???????????? ?????? ????????????.
            // 2. ???????????? ???????????? |&&|???????????? ???????????? ??????. ??????????????? ??? ??????
            // 3. fixedValue??? ???????????? ????????? fixedValue????????? ?????????

            // 1. splitter??? ????????? ????????? ????????? ??????
            splitterMap.entrySet().stream().forEach(mergeMap -> {
                // viewDetails 
                DetailDto matchedDetail = headerDto.getHeaderDetail().getDetails().stream().filter(r -> r.getMatchedColumnName().equals(mergeMap.getKey())).collect(Collectors.toList()).get(0);
                String appendFieldValue = "";

                for (int j = 0; j < matchedDetail.getViewDetails().size(); j++) {
                    appendFieldValue += CustomFieldUtils.getFieldValue(originDto, matchedDetail.getViewDetails().get(j).getMatchedColumnName()).toString();
                    if (j < matchedDetail.getViewDetails().size() - 1) {
                        appendFieldValue += mergeMap.getValue().toString();
                    }
                }
                CustomFieldUtils.setFieldValue(currentVo, mergeMap.getKey(), appendFieldValue);
            });
        }


        // 2. ????????? ???????????? |&&|???????????? ???????????? ??????.
        List<ErpOrderItemVo> mergeItemVos = new ArrayList<>();

        Set<String> deliverySet = new HashSet<>();
        for (int i = 0; i < itemVos.size(); i++) {
            StringBuilder sb = new StringBuilder();
            sb.append(itemVos.get(i).getReceiver());
            sb.append(itemVos.get(i).getReceiverContact1());
            sb.append(itemVos.get(i).getDestination());

            String resultStr = sb.toString();

            mergeItemVos.add(itemVos.get(i));
            int currentMergeItemIndex = mergeItemVos.size() - 1;

            // ???????????????(?????? + ??????)
            if (!deliverySet.add(resultStr)) {
                ErpOrderItemVo currentVo = mergeItemVos.get(currentMergeItemIndex);
                ErpOrderItemVo prevVo = mergeItemVos.get(currentMergeItemIndex - 1);

                splitterMap.entrySet().stream().forEach(mergeMap -> {
                    String prevFieldValue = CustomFieldUtils.getFieldValue(prevVo, mergeMap.getKey()) == null ? "" : CustomFieldUtils.getFieldValue(prevVo, mergeMap.getKey());
                    String currentFieldValue = CustomFieldUtils.getFieldValue(currentVo, mergeMap.getKey()) == null ? "" : CustomFieldUtils.getFieldValue(currentVo, mergeMap.getKey());
                    CustomFieldUtils.setFieldValue(prevVo, mergeMap.getKey(), prevFieldValue + "|&&|" + currentFieldValue);
                });

                // ??????????????? ??????
                mergeItemVos.remove(currentMergeItemIndex);
            }

            // 3. fixedValue??? ????????? column?????? fixedValue????????? ???????????? ???????????????
            fixedValueMap.entrySet().stream().forEach(map -> {
                CustomFieldUtils.setFieldValue(mergeItemVos.get(mergeItemVos.size() - 1), map.getKey(), map.getValue());
            });
        }

        return mergeItemVos;
    }

    public ErpSecondMergeHeaderDto searchErpSecondMergeHeader(UUID secondMergeHeaderId) {
        ErpSecondMergeHeaderEntity secondMergeHeaderEntity = erpSecondMergeHeaderService.searchOne(secondMergeHeaderId);
        return ErpSecondMergeHeaderDto.toDto(secondMergeHeaderEntity);
    }

    public List<WaybillExcelFormDto> readWaybillExcelFile(MultipartFile file) {
        if (!CustomExcelUtils.isExcelFile(file)) {
            throw new CustomExcelFileUploadException("????????? ????????? ????????? ????????????.\n[.xls, .xlsx] ????????? ????????? ???????????????.");
        }

        List<String> HEADER_NAMES = WaybillExcelFormManager.HEADER_NAMES;
        List<String> FIELD_NAMES = WaybillExcelFormManager.getAllFieldNames();
        List<Integer> REQUIRED_CELL_NUMBERS = WaybillExcelFormManager.REQUIRED_CELL_NUMBERS;

        Integer SHEET_INDEX = 0;
        Integer HEADER_ROW_INDEX = WaybillExcelFormManager.HEADER_ROW_INDEX;
        Integer DATA_START_ROW_INDEX = WaybillExcelFormManager.DATA_START_ROW_INDEX;
        Integer ALLOWED_CELL_SIZE = WaybillExcelFormManager.ALLOWED_CELL_SIZE;

        Workbook workbook = CustomExcelUtils.getWorkbook(file);
        Sheet worksheet = workbook.getSheetAt(SHEET_INDEX);
        Row headerRow = worksheet.getRow(HEADER_ROW_INDEX);

//        ?????? ????????? ?????? ?????????
        List<WaybillExcelFormDto> waybillExcelFormDtos = new ArrayList<>();

//        ?????? ?????? ?????? => cell size, header cell name check
        if (
                !CustomExcelUtils.getCellCount(worksheet, HEADER_ROW_INDEX).equals(ALLOWED_CELL_SIZE) ||
                        !CustomExcelUtils.isCheckedHeaderCell(headerRow, HEADER_NAMES)
        ) {
            throw new CustomExcelFileUploadException("????????? ????????? ?????? ????????? ????????????.\n????????? ?????? ????????? ????????? ????????????.");
        }

//        ?????? ????????? ?????? ????????? Row Loop
        for (int i = DATA_START_ROW_INDEX; i < worksheet.getPhysicalNumberOfRows(); i++) {
            Row row = worksheet.getRow(i);
            WaybillExcelFormDto waybillExcelFormDto = new WaybillExcelFormDto();

//            Cell Loop
            for (int j = 0; j < ALLOWED_CELL_SIZE; j++) {
                Cell cell = row.getCell(j);
                CellType cellType = cell.getCellType();
                Object cellValue = new Object();

//                ?????? ????????? ?????? ???????????? ???????????? ?????? dto??? Null??? ???????????? break
                if (REQUIRED_CELL_NUMBERS.contains(j) && cellType.equals(CellType.BLANK)) {
                    waybillExcelFormDto = null;
                    break;
                }

//                cellValue ????????????
                cellValue = CustomExcelUtils.getCellValueObject(cell, CustomExcelUtils.NUMERIC_TO_INT);
//                cellValue dto??? ???????????????
                CustomFieldUtils.setFieldValueWithSuper(waybillExcelFormDto, FIELD_NAMES.get(j), cellValue.toString());
            }

//            dto??? ?????? ???????????? ???????????? ?????????.
            if (waybillExcelFormDto != null) {
                waybillExcelFormDtos.add(waybillExcelFormDto);
            }
        }

        return waybillExcelFormDtos;
    }

    @Transactional
    public int changeBatchForWaybill(List<ErpOrderItemDto> erpOrderItemDtos, List<WaybillExcelFormDto> waybillExcelFormDtos) {
        List<WaybillExcelFormDto> dismantledWaybillExcelFormDtos = new ArrayList<>();
        waybillExcelFormDtos.stream().forEach(r -> {
            List<String> freightCodes = List.of(r.getFreightCode().split(","));

            freightCodes.stream().forEach(freightCode -> {
                WaybillExcelFormDto dto = new WaybillExcelFormDto();
                dto.setReceiver(r.getReceiver());
                dto.setFreightCode(freightCode);
                dto.setWaybillNumber(r.getWaybillNumber());
                dto.setTransportType(r.getTransportType());
                dto.setCourier(r.getCourier());

                dismantledWaybillExcelFormDtos.add(dto);
            });
        });

        List<UUID> ids = erpOrderItemDtos.stream().map(r -> r.getId()).collect(Collectors.toList());
        List<ErpOrderItemEntity> erpOrderItemEntities = erpOrderItemService.findAllByIdList(ids);
        AtomicInteger updatedCount = new AtomicInteger();

        erpOrderItemEntities.forEach(erpOrderItemEntity -> {
            String matchingData = erpOrderItemEntity.getReceiver() + erpOrderItemEntity.getFreightCode();
            dismantledWaybillExcelFormDtos.forEach(waybillExcelFormDto -> {
                String matchedData = waybillExcelFormDto.getReceiver() + waybillExcelFormDto.getFreightCode();

                if (matchingData.equals(matchedData)) {
                    erpOrderItemEntity.setWaybillNumber(waybillExcelFormDto.getWaybillNumber());
                    erpOrderItemEntity.setTransportType(waybillExcelFormDto.getTransportType());
                    erpOrderItemEntity.setCourier(waybillExcelFormDto.getCourier());
                    updatedCount.getAndIncrement();
                }
            });
        });

        return updatedCount.get();
    }

    @Transactional
    public Integer actionReflectStock(List<ErpOrderItemDto> itemDtos) {
        Set<String> optionCodeSet = new HashSet<>();
        List<ErpOrderItemEntity> erpOrderItemEntities = erpOrderItemService.getEntities(itemDtos);
        List<ProductOptionEntity> productOptionEntities = new ArrayList<>();
        List<ReleaseStockEntity> releaseStockEntities = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();

        for (ErpOrderItemEntity r : erpOrderItemEntities) {
            if (!r.getReleaseOptionCode().isEmpty()) {
                optionCodeSet.add(r.getReleaseOptionCode());
            }
        }

        productOptionEntities = productOptionService.searchListByOptionCodes(new ArrayList<>(optionCodeSet));

        if (productOptionEntities.size() <= 0) {
            return 0;
        }

        for (ErpOrderItemEntity orderItemEntity : erpOrderItemEntities) {
            productOptionEntities.forEach(optionEntity -> {
                if (optionEntity.getCode().equals(orderItemEntity.getReleaseOptionCode()) && orderItemEntity.getStockReflectYn().equals("n")) {
                    count.getAndIncrement();
                    ReleaseStockEntity releaseStockEntity = new ReleaseStockEntity();

                    releaseStockEntity.setId(UUID.randomUUID());
                    releaseStockEntity.setErpOrderItemId(orderItemEntity.getId());
                    releaseStockEntity.setReleaseUnit(orderItemEntity.getUnit());
                    releaseStockEntity.setMemo("");
                    releaseStockEntity.setCreatedAt(CustomDateUtils.getCurrentDateTime());
                    releaseStockEntity.setCreatedBy(orderItemEntity.getCreatedBy());
                    releaseStockEntity.setProductOptionCid(optionEntity.getCid());
                    releaseStockEntity.setProductOptionId(optionEntity.getId());

                    orderItemEntity.setStockReflectYn("y");
                    releaseStockEntities.add(releaseStockEntity);
                }
            });
        }

        releaseStockService.bulkInsert(releaseStockEntities);
        return count.get();
    }

    @Transactional
    public Integer actionCancelStock(List<ErpOrderItemDto> itemDtos) {
        itemDtos = itemDtos.stream().filter(r -> r.getStockReflectYn().equals("y")).collect(Collectors.toList());
        List<ErpOrderItemEntity> erpOrderItemEntities = erpOrderItemService.getEntities(itemDtos);
        List<UUID> erpOrderItemIds = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();

        for (ErpOrderItemEntity orderItemEntity : erpOrderItemEntities) {
            count.getAndIncrement();
            erpOrderItemIds.add(orderItemEntity.getId());
            orderItemEntity.setStockReflectYn("n");
        }

        releaseStockService.deleteByErpOrderItemIds(erpOrderItemIds);
        return count.get();
    }
}
