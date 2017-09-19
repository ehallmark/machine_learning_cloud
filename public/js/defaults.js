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
        $(containerSelector+" "+itemSelector).find('textarea,input,select').each(function() {
            var $elem = $(this);
            if($elem.attr('id') && ! $elem.prop('disabled')) {
                dataMap[$elem.attr("id")]=$elem.val();
            }
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
                   if($('#results #data-table table thead th').length > 0) {
                   $('#results #data-table table').dynatable({
                     dataset: {
                       ajax: true,
                       ajaxUrl: 'dataTable.json',
                       ajaxOnLoad: true,
                       records: []
                     }
                   });
               }

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

    // nested forms
    $('select.nested-filter-select').each(function() {
        $this = $(this);
        var displayItemSelectOptions = {width: '100%', placeholder: 'Search', closeOnSelect: true};
        $this.select2(displayItemSelectOptions);
    });

    $('select.nested-filter-select').on("change", function(e) {
        var $options = $(e.currentTarget.selectedOptions);
        var $hiddenOptions = $(e.currentTarget).find("option").not($options);
        $hiddenOptions.each(function(i,option){
            var id = $(option).val();
            var $draggable = $('.draggable[data-model="'+id+'"]');
            $draggable.find('input, select, textarea').prop('disabled', true).val(null).filter('.nested-filter-select').trigger('change');
            $draggable.parent().hide();
            return true;
        });
        $options.each(function(i,option){
            var id = $(option).val();
            var $draggable = $('.draggable[data-model="'+id+'"]');
            $draggable.find('input, select, textarea').prop('disabled', false).filter('.nested-filter-select').trigger('change');
            $draggable.parent().show();
        });
        return true;
    });

    $('select.nested-filter-select').on("select2:select", function() {

    });

    $('.sidebar .nav-item .btn').click(function(e){
        $('.sidebar .nav-item .btn').removeClass('active');
        $(this).addClass('active');
    });

    $('.draggable .collapsible-header .remove-button').click(function(e) {
        e.stopPropagation();
        var $draggable = $(this).closest('.draggable');
        // get name
        var label = $draggable.attr('data-model');
        var $select = $draggable.closest('.nested-form-list').prev().find('select.nested-filter-select');
        var values = $select.val();
        var idx = $.inArray(label,values);
        if(idx >= 0) {
            values.splice(idx,1);
            $select.val(values).trigger('change');
        }
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

    $('.nested-form-list').sortable();
    $('.nested-form-list').disableSelection();

    $('#main-content-id').addClass('show');
});

var resetSearchForm = function() {
    $('.target .collapsible-header .remove-button').click();
    $('.draggable').find('select,textarea,input').prop("disabled",true).val(null).trigger('change');
    $('#results').html('');
};

var findByValue = function(inputs, value) {
    for(var i = 0; i < inputs.length; i++) {
        if(inputs[i].value == value) return inputs[i];
    }
    return null;
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

var showTemplateFormHelper = function(formSelector,json) {
    var dataMap = jQuery.parseJSON(json);
    $.each(dataMap,function(id,value) {
        var $elem = $('#'+id);
        $elem.closest('draggable').parent().show();
        $elem.val(value);
        $elem.trigger('change');
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
