$(document).ready(function() {
    $('.miniTip').miniTip({
        title: 'Advanced Keyword Syntax',
        event: 'click',
        content: "<p>+ signifies AND operation</p>"+
                 "<p>| signifies OR operation</p>"+
                 "<p>- negates a single token</p>"+
                 "\"\" wraps a number of tokens to signify a phrase for searching</p>"+
                 "<p>* at the end of a term signifies a prefix query</p>"+
                 "<p>( and ) signify precedence</p>"+
                 "<p>~N after a word signifies edit distance (fuzziness)</p>"+
                 "<p>~N after a phrase signifies slop amount</p>"
    });

    var saveTemplateFormHelper = function(containerSelector,itemSelector,hiddenValueSelector) {
        var dataMap = {};
        $(containerSelector+" "+itemSelector).each(function() {
            $(this).find("textarea,input,select").each(function() {
                var $elem = $(this);
                if($elem.attr('id') && ! $elem.prop('disabled')) {
                    dataMap[$elem.attr("id")]=$elem.val();
                }
            });
        });
        var json = JSON.stringify(dataMap);
        $(hiddenValueSelector).val(json);
    };


    $('#save-template-form-id').submit(function(e){
        e.preventDefault();
        saveTemplateFormHelper("#searchOptionsForm",".attributeElement","#save-template-form-id #searchOptionsMap");
        saveTemplateFormHelper("#attributesForm",".attributeElement","#save-template-form-id #attributesMap");
        saveTemplateFormHelper("#filtersForm",".attributeElement","#save-template-form-id #filtersMap");
        saveTemplateFormHelper("#chartsForm",".attributeElement","#save-template-form-id #chartsMap");

        var $this = $(this);

        $.ajax({
          type: "POST",
          url: $(this).attr('action'),
          data: $(this).serialize(),
          success: function(data) {
            // add button
            if(!(data.hasOwnProperty('name') && data.hasOwnProperty('chartsMap') && data.hasOwnProperty('attributesMap') && data.hasOwnProperty('filtersMap') && data.hasOwnProperty('searchOptionsMap') && data.hasOwnProperty('file'))) {
                alert('Error saving template: '+data.message);
            } else {
                var name = data.name;
                var charts = data.chartsMap;
                var attributes = data.attributesMap;
                var filters = data.filtersMap;
                var searchOptions = data.searchOptionsMap;
                var file = data.file;
                $('#my-templates').append($("<li class='nav-item'><button class='btn btn-secondary template-show-button' style='width: 70%;' data-name='"+name+"' data-chartsmap='"+charts+"' data-attributesmap='"+attributes+"' data-filtersmap='"+filters+"' data-searchoptionsmap='"+searchOptions+"'>"+name+"</button><span data-action='/secure/delete_template' data-file='"+file+"' class='template-remove-button' >X</span></li>"));
                $('.template-show-button').filter(':last').click(showTemplateFunction);
                $('.template-remove-button').filter(':last').click(removeTemplateFunction);
                $('#template_name').val(null);
            }
          },
          dataType: "json"
        });

        return false;
    });

    $('.template-show-button').click(showTemplateFunction);

    var submitFormFunction = function(e,onlyExcel) {
         e.preventDefault();
         var buttonId, buttonTextWhileSearching,buttonText;
         if(onlyExcel) {
            buttonId = "download-to-excel-button";
            buttonText = "Download to Excel";
            buttonTextWhileSearching = "Downloading...";
         } else {
            buttonId = "generate-reports-form-button";
            buttonText = "Generate Report";
            buttonTextWhileSearching = "Generating...";
         }
         var formId = "generate-reports-form";
         var $form = $('#'+formId);
         var $button = $('#'+buttonId);
         var url = $form.attr('action');
         var tempScrollTop = $(window).scrollTop();

         $button.attr('disabled',true).text(buttonTextWhileSearching);
         $form.find('#only-excel-hidden-input').val(onlyExcel);

         if(!onlyExcel) {  $('#results').html('');  }  // clears results div
         $.ajax({
           type: 'POST',
           dataType: 'json',
           url: url,
           data: $form.serialize(),
           complete: function(jqxhr,status) {
             $button.attr('disabled',false).text(buttonText);
             $(window).scrollTop(tempScrollTop);
           },
           error: function(jqxhr,status,error) {
             $('#results').html('<div style="color: red;">Server error during ajax request:'+error+'</div>');
           },
           success: function(data) {
             if(onlyExcel) {
                var $downloadForm = $('<form method="post" action="/secure/excel_generation"></form>');
                $downloadForm.appendTo('body').submit().remove();
             } else {
               $('#results').html(data.message);
               $('#results #data-table table').dynatable({
                 dataset: {
                   ajax: true,
                   ajaxUrl: 'dataTable.json',
                   ajaxOnLoad: true,
                   records: []
                 }
               });

               setCollapsibleHeaders('#results .collapsible-header');

               if (data.hasOwnProperty('chartCnt')) {
                 try {
                   var chartCnt = data.chartCnt;
                   if(chartCnt > 0) {
                     for(var i = 0; i<chartCnt; i++) {
                       var idx = i;
                       $.ajax({
                         type: "POST",
                         dataType: "json",
                         url: "charts",
                         data: { chartNum: idx },
                         success: function(chartData) {
                           if(chartData.hasOwnProperty('message')) {
                            // error
                              alert(chartData.message);
                           } else {
                              var $chartDiv = $('#'+chartData.chartId.toString());
                              for(var j = 0; j < chartData.charts.length; j++) {
                                $('<div id="'+ chartData.chartId+"-"+j.toString() +'"></div>').appendTo($chartDiv);
                                var chartJson = chartData.charts[j];
                                var chart = Highcharts.chart(chartData.chartId+"-"+j.toString(), chartJson);
                                chart.redraw();
                              }
                           }
                         }
                       });
                     }
                   }
                 } catch (err) {
                   $('#results').html("<div style='color:red;'>JavaScript error occured while rendering charts: " + err.message + '</div>');
                 }
               }
             }
           }
         });
         return false;
     };

    $('#generate-reports-form').submit(function(e) {return submitFormFunction(e,false);});
    $('#generate-reports-form-button').click(function(e) {return submitFormFunction(e,false);});

    $('#download-to-excel-button').click(function(e) {return submitFormFunction(e,true);});

    $('.template-remove-button').click(removeTemplateFunction);

    // display-item-select
    $('.display-item-select').each(function(){
        $this = $(this);
        var displayItemSelectOptions = {width: '100%', placeholder: $this.find('option.placeholder').text()};
        $this.select2(displayItemSelectOptions);
    });

    // On opening
    $('.display-item-select').on("select2:opening", function(e){
        $this = $(this);

        // placeholder data
        var $placeholder = $this.find('option.placeholder').detach();

        // delete all items of the native select element
        $this.parent().find(".hidden-placeholder").html($placeholder);
        $this.find("optgroup").empty();
        $this.find("option").remove();

        // add hidden elements
        $this.prepend('<option></option>');
        var $items = $this.parent().next().find('.draggable .collapsible-header:not(.nested) label');

        // add new items
        $items.each(function(index, elem) {
            var $optGroup = $(elem).attr('opt-group');
            var value = $(elem).closest('.attributeElement').attr("data-model");
            if($optGroup.length>0) {
                $this.find('optgroup[name="'+$optGroup+'"]').append('<option value="'+value+'">'+$(elem).text()+"</option>");
            } else {
                $this.append('<option value="'+value+'">'+$(elem).text()+"</option>");
            }
        });

        $this.trigger('change');
    });

    // on select
    $('.display-item-select').on("select2:select", function(e){
        $this = $(this);
        var value = $(this).attr('data-value');
        if(value!=null&&value.length>0) {
            var toDisplay = $this.parent().next().find('.attributeElement[data-model="'+value+'"]').get(0);
            showDraggable(toDisplay);
        }
    });

    // on close
    $('.display-item-select').on("select2:close", function(e){
        $this = $(this);
        var $placeholder = $this.parent().find(".hidden-placeholder").find('option.placeholder').clone();
        if(e.params.hasOwnProperty('originalSelect2Event')) {
            var value = e.params.originalSelect2Event.data.id;
            $(this).attr('data-value',value);
        }
        $this.prepend($placeholder);
        $this.trigger('change');
    });

    // nested forms
    $('select.nested-filter-select').each(function() {
        $this = $(this);
        var displayItemSelectOptions = {width: '100%', placeholder: 'Search', closeOnSelect: true};
        $this.select2(displayItemSelectOptions);
    });

    $('select.nested-filter-select').on("select2:select", function(e) {
        var id = e.params.data.id;
        $('.draggable[data-model="'+id+'"]').parent().show().find('input, select, textarea').prop('disabled', false);
        return true;
    });

    $('select.nested-filter-select').on("select2:unselect", function(e) {
        var id = e.params.data.id;
        $('.draggable[data-model="'+id+'"]').parent().hide().find('input, select, textarea').prop('disabled', true);
        return true;
    });

    $('.sidebar .nav-item .btn').click(function(e){
        $('.sidebar .nav-item .btn').removeClass('active');
        $(this).addClass('active');
    });

    $(".mycheckbox").on("click", function (e) {
        var checkbox = $(this);
        // do the confirmation thing here
        e.preventDefault();
        return false;
    });

    $('.draggable .collapsible-header .remove-button').click(function(e) {
        e.stopPropagation();
        hideDraggable($(this).parent().get(0));
    });

    $('.multiselect').select2({
        minimumResultsForSearch: 5,
        closeOnSelect: false
    });


    $('.single-select2').select2({
        minimumResultsForSearch: 5,
        width: "100%"
    });

    setCollapsibleHeaders(".collapsible-header");

    $(document).tooltip({
        content: function() { return $(this).attr('title'); }
    });

    resetSearchForm();
});

var setupDataTable = function (dataTable) {
    $(dataTable).find('th.sortable').on("click",function() {
        var f = -1;
        if($(this).attr("data-sorted")) {
            f=1;
            $(this).removeAttr("data-sorted");
        } else {
            $(this).attr("data-sorted","true");
        }

        var sortOnField = $(this).data('field');
        var $wrapper = $(dataTable).find('tbody');
        var rows = $wrapper.find("tr").get();

        rows.sort(function(a, b) {

            var A = getVal(a,sortOnField);
            var B = getVal(b,sortOnField);

            if(A > B) {
                return 1*f;
            } else if(A < B) {
                return -1*f;
            }
            return 0;
        });


        $.each(rows, function(){
            $wrapper.append(this);
        });


    });

    var getVal = function (elm,sortOnField) {
        var v = $(elm).data(sortOnField);
        if($.isNumeric(v)){
            v = parseFloat(v);
        }
        return v;
    };
};

var resetSearchForm = function() {
    $('.target .collapsible-header .remove-button').click();
    $('.highlighted').removeClass('highlighted');
    $('.highlighted-special').removeClass('highlighted-special');
    $('.draggable').find('select,textarea,input').prop("disabled",true).not("input.mycheckbox").val(null).trigger('change');
    $('#results').html('');
};

var findByValue = function(inputs, value) {
    for(var i = 0; i < inputs.length; i++) {
        if(inputs[i].value == value) return inputs[i];
    }
    return null;
};


var paramsHelper = function(input,value) {
    var $checkbox = input;
    if(! input.hasClass("mycheckbox")) {
        $checkbox = input.closest('.draggable').find('.mycheckbox');
        input.val(value);
        if(input.hasClass("multiselect")) {
            input.trigger('change');
        }
    }
    var $dropZone = $checkbox.closest('.droppable');
    if(! $dropZone.hasClass('target')) {
        if($checkbox.hasClass('mycheckbox')) {
            showDraggable($checkbox.parent().get(0));
        }
    }
    if(!$checkbox.parent().hasClass("highlighted")) {
        $checkbox.parent().addClass('highlighted');
    }
    return $checkbox;
};

var setCollapsibleHeaders = function(selector) {
    $(selector).click(function () {
        $header = $(this);
        //getting the next element
        $content = $($header.attr("data-target"));
        if((! $content.hasClass("show")) && $content.attr("id")==="main-content-id") {
            $("html, body").animate({
                scrollTop: 0
            }, 500);
        }
        if($content.hasClass("show")) {
            $content.css("max-height", "none");
            var height = $content.outerHeight();
            $content.css("max-height",height.toString());
            $content.animate({
                maxHeight: 0
            }, {
                duration: 400,
                always: function() {
                    $content.removeClass("show");
                    $content.css("max-height","");
                }
            });

        } else {
            // get height
            $content.css("max-height", "none");
            var height = $content.outerHeight();

            // reset to 0 then animate with small delay
            $content.addClass("show");
            $content.css("max-height", "0");
            $content.animate({
                maxHeight: height
            }, {
                duration: 400,
                always: function() {
                    $content.css("max-height", "");
                }
            });
        }
    });
};

var resetCheckbox = function(elem,target,shouldShow) {
    var $draggable = $(elem);
    // check for nested
    if($draggable.hasClass('nested')) {
        resetCheckbox($draggable.parent().closest('.draggable').get(0), target, shouldShow);
        var $handle = $draggable.find(".collapsible-header");
        var $collapse = $draggable.find(".collapse");
        $collapse.css('display','');
        if(shouldShow && !$collapse.hasClass('show')) {
            $collapse.addClass('show');
        } else if ($collapse.hasClass('show') && !shouldShow) {
            $collapse.removeClass('show');
        }
        // actually show element incase it's not shown
        if(shouldShow && !$draggable.is(':visible')) {
            $draggable.parent().show();
        } else if ($draggable.is(':visible') && !shouldShow) {
            $draggable.parent().hide();
        }

    } else {
        $draggable.detach().css({top: 0,left: 0}).appendTo(target);
        var $handle = $draggable.find(".collapsible-header");
        var $collapse = $draggable.find(".collapse");
        $collapse.css('display','');
        if(shouldShow && !$collapse.hasClass('show')) {
            $collapse.addClass('show');
        } else if ($collapse.hasClass('show') && !shouldShow) {
            $collapse.removeClass('show');
        }

        var $checkbox = $draggable.find(".mycheckbox")
        $checkbox.prop("checked", shouldShow);

    }
};

var showDraggable = function(elem) {
    var $draggable = $(elem);
    if(!$draggable.hasClass("draggable")) $draggable = $draggable.closest('.draggable');
    if($draggable.length > 0) {
        if($draggable.hasClass('leaf')) {
            ($draggable.hasClass('nested') ? $draggable.parent() : $draggable).find('input,textarea,select').prop("disabled",false);
        } else {
            // enable the select option
            $draggable.find('select.nested-filter-select,select.nested-attribute-select').prop('disabled', false);
            $draggable.children().first().children('input.mycheckbox').prop('disabled',false);
        }
        var id = $draggable.attr('data-target');
        if(id) {
            var target = "target";
            $target = $('#'+id+'-'+target);
            if($target) {
                  resetCheckbox($draggable.get(0),$target.get(0),true);
            }
        }
    }
};

var hideDraggable = function(elem) {
    var $draggable = $(elem);
    if(!$draggable.hasClass("draggable")) $draggable = $draggable.closest('.draggable');
    if($draggable.length > 0) {
        ($draggable.hasClass('nested') ? $draggable.parent() : $draggable).find('select,textarea,input').prop("disabled",true).not("input.mycheckbox").val(null).trigger('change');
        if(!$draggable.hasClass('leaf')) {
            $draggable.children().each(function(){ $(this).find('.draggable').each(function(){$(this).parent().hide();}); });
        }
        var id = $draggable.attr('data-target');
        if(id) {
            var target = "start";
            $target = $('#'+id+'-'+target);
            if($target) {
                  resetCheckbox($draggable.get(0),$target.get(0),false);
            }
        }
    }
};

var showTemplateFormHelper = function(formSelector,json) {
    var dataMap = jQuery.parseJSON(json);
    $.each(dataMap,function(id,value) {
        var $elem = $('#'+id);
        showDraggable($elem.get(0));
        if(! $elem.hasClass("mycheckbox")) {
            $elem.val(value);
            $elem.trigger('change');
        }
    });
};

var showTemplateFunction = function(e){
    e.preventDefault();
    var $this = $(this);
    resetSearchForm();
    showTemplateFormHelper("#searchOptionsForm",$this.attr("data-searchOptionsMap"));
    showTemplateFormHelper("#attributesForm",$this.attr("data-attributesMap"));
    showTemplateFormHelper("#filtersForm",$this.attr("data-filtersMap"));
    showTemplateFormHelper("#chartsForm",$this.attr("data-chartsMap"));
    return false;
};

var removeTemplateFunction = function(e){
     e.preventDefault();
     var $this = $(this);
     var $li = $this.closest('li');
     $this.remove();
     $.ajax({
       type: "POST",
       url: $(this).attr('data-action'),
       data: {
         path_to_remove: $(this).attr("data-file")
       },
       success: function(data) {
         $li.remove();
       },
       dataType: "json"
     });
     return false;
}

var loadEvent = function(){
 // do nothing :(
};
