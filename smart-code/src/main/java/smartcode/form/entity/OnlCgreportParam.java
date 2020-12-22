//
// Source code recreated from cgreportAUtils .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package smartcode.form.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("onl_cgreport_param")
public class OnlCgreportParam implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(
        type = IdType.ASSIGN_ID
    )
    private String id;
    private String cgrheadId;
    private String paramName;
    private String paramTxt;
    private String paramValue;
    private Integer orderNum;
    private String createBy;
    @JsonFormat(
        timezone = "GMT+8",
        pattern = "yyyy-MM-dd HH:mm:ss"
    )
    @DateTimeFormat(
        pattern = "yyyy-MM-dd HH:mm:ss"
    )
    private Date createTime;
    private String updateBy;
    @JsonFormat(
        timezone = "GMT+8",
        pattern = "yyyy-MM-dd HH:mm:ss"
    )
    @DateTimeFormat(
        pattern = "yyyy-MM-dd HH:mm:ss"
    )
    private Date updateTime;
}
