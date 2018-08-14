
(function($) {
  $.extend($.expr[':'], {
    'off-top': function(el) {
      return $(el).offset().top < $(window).scrollTop();
    },
    'off-right': function(el) {
      return $(el).offset().left + $(el).outerWidth() - $(window).scrollLeft() > $(window).width();
    },
    'off-bottom': function(el) {
      return $(el).offset().top + $(el).outerHeight() - $(window).scrollTop() > $(window).height();
    },
    'off-left': function(el) {
      return $(el).offset().left < $(window).scrollLeft();
    },
    'off-screen': function(el) {
      return $(el).is(':off-top, :off-right, :off-bottom, :off-left');
    }
  });
})(jQuery);


var selectionCache = new Set([]);
var previewSelectionCache = new Set([]);
var formIDRequestedCache = new Set([]);
var editor = null;

var update_expert_query_display = function() {
    // text area
    if(!editor) return;
    editor.setValue($('#acclaim_expert_filter').val());
};

var update_expert_query_text_area = function() {
    // text area
    if(!editor) return;
    $('#acclaim_expert_filter').val(editor.getValue());
};

var nestedFilterSelectFunction = function(e,preventHighlight) {
     var $options = $(e.currentTarget.selectedOptions);
     var $select = $(this);
     var $selectWrapper = $select.parent().parent();
     var addedDraggables = [];

     var $hiddenOptions = $(e.currentTarget).find("option");
     if($options.length>0) { $hiddenOptions = $hiddenOptions.not($options); }
     $hiddenOptions.each(function(i,option){
         var id = $(option).val();
         var $draggable = $selectWrapper.find('.attributeElement[data-model="'+id+'"]');

         // update attributes
         var divAttribute = $draggable.attr('data-attribute');
         if(divAttribute) {
            $draggable.find('#'+divAttribute).addClass("disabled");
         }
         var inputs = $draggable.data('inputs');
         if(inputs && inputs.length > 0) {
            $.each(inputs,function() {
                var $this = $('#'+this);
                if($this.length == 0) {
                    alert('Missing: '+this);
                } else {
                    $this.prop('disabled',true);
                    if($this.attr('type')==='checkbox') {
                        $this.prop('checked', false);
                    } else {
                        $this.val(null).filter('select').trigger('change', [preventHighlight]);
                    }
                }
            });
         }

         $draggable.parent().hide();
         return true;
     });

     var $formList = $select.parent().next();
     // get any missing elements first
     for(var i = 0; i < $options.length; i++) {
         var option = $options.get(i);
         var id = $(option).val();
         var newElemId = $(option).attr('data-id');
         if($('#'+newElemId).length==0 && !formIDRequestedCache.has(newElemId)) {
             // get option from server
             formIDRequestedCache.add(newElemId);
             $.ajax({
                 type: "POST",
                 url: '/form_elem_by_id',
                 data: {
                     id: newElemId
                 },
                 success: function(data) {
                     if(data && data.hasOwnProperty('results')) {
                         var $new = $(data.results);
                         $formList.append($new);
                         $new.show();
                         var $newFilters = $new.find('.nested-filter-select');
                         setupNestedFilterSelects($newFilters, $new);
                     }
                 },
                 dataType: "json"
             });
         }
     }

     $options.each(function(i,option){
          var id = $(option).val();
          var newElemId = $(option).attr('data-id');
          var $draggable = $selectWrapper.find('.attributeElement[data-model="'+id+'"]');
          var wasHidden = $draggable.parent().is(':hidden');
          if(!preventHighlight && wasHidden) {
              addedDraggables.push($draggable);
          }

          // update attributes
          var divAttribute = $draggable.attr('data-attribute');
          if(divAttribute) {
             $draggable.find('#'+divAttribute).removeClass("disabled");
          }
          var inputs = $draggable.data('inputs');
          if(inputs && inputs.length > 0) {
             $.each(inputs,function() {
                 var $this = $('#'+this);
                 if($this.length == 0) {
                     alert('Missing: '+this);
                 } else {
                     $('#'+this).prop('disabled', false).trigger('focus').filter('select').trigger('change', [preventHighlight]);
                 }
             });
          }

          $draggable.parent().show();
     });

     // sort this
     $selectWrapper.find('.nested-form-list').filter(':first').each(function() {
          var list = $(this);
          var elems = list.children().filter('[sort-order]').detach();
          if(elems.length>0) {
              elems.sort(function(a,b) {
                  var i = parseInt($(a).attr("sort-order"));
                  var j = parseInt($(b).attr("sort-order"));
                  return (i > j) ? 1 : (i < j) ? -1 : 0;
              });
              // remove sort
              elems.removeAttr("sort-order");
              list.append(elems);
              list.sortable('refreshPositions');
          }
     });


     if(addedDraggables.length == 1) { // added by human
          $selectWrapper.find('.highlight').removeClass('highlight');
          $.each(addedDraggables, function() {
              var $draggable = $(this);
              $draggable.addClass('highlight');
              $draggable.click(function() { // highlight until clicked
                  $(this).removeClass('highlight');
              });
              var $parent = $draggable.parent();
              $parent.parent().prepend($parent);
          });
     }
     return true;
}

var createTooltips = function($elems, placement) {
    $elems.tooltip({
        trigger: 'manual',
        container: 'body',
        placement: placement,
        delay: {
            "show": 400,
            "hide": 200
        },
        html: true
    }).on("mouseenter", function () {
        var _this = this;
        $('.tooltip').remove();
        $(_this).tooltip("show");
        $(".tooltip").on("mouseleave click", function () {
            $(_this).tooltip('hide');
        });
    }).on("mouseleave", function () {
        var _this = this;
        setTimeout(function () {
            if (!$(".tooltip:hover").length) {
                $(_this).tooltip("hide");
            }
        }, 300);
    }).on("shown.bs.tooltip", function() {
        var $tip = $('.tooltip');
        var $arrow = $tip.find('.arrow');
        $tip.addClass('fade');
        if($tip.is(":off-left")) {
            var width = $tip.width() * 0.33;
            $tip.css('left', width);
            $arrow.css('left', $tip.width()/2.0);
        }
    });

};

var setupNestedFilterSelects = function($selects, $topLevelElem) {
    var displayItemSelectOptions = {width: '100%', placeholder: 'Search', closeOnSelect: true};
    // nested forms

    $selects.each(function(){
        $(this).attr('title', 'Click to view available options...');
    });
    $selects.select2(displayItemSelectOptions);
    $selects.on("change", nestedFilterSelectFunction);

    $topLevelElem.find('.attributeElement .collapsible-header .remove-button').click(function(e) {
        e.stopPropagation();
        // get name
        var label = $(this).attr('data-model');
        var $select = $($(this).attr('data-select'));
        var values = $select.val();
        var idx = $.inArray(label,values);
        if(idx >= 0) {
            values.splice(idx,1);
            $select.val(values).trigger('change',[true]);
        }
    });


    $topLevelElem.find('.multiselect').select2({
        minimumResultsForSearch: 5,
        closeOnSelect: false,
        templateSelection: select2SelectedFunction
    });

    // how results look in the dropdown
    var ajaxMultiTemplateResultFunction = function(elem) {
        if (!elem.html_result) {
            return elem.text;
        }
        return $(elem.html_result);
    };

    $topLevelElem.find('.multiselect-ajax').select2({
      width: "100%",
      ajax: {
        url: function() { return $(this).attr("data-url"); },
        dataType: "json",
        delay: 100,
        data: function(params) {
            var query = {
                search: params.term,
                page: params.page || 1
            };

            return query;
        }
      },
      templateResult: ajaxMultiTemplateResultFunction
    });

    $topLevelElem.find('.single-select2').select2({
        minimumResultsForSearch: 10,
        width: "100%",
        templateSelection: select2SelectedFunction
    });

    $topLevelElem.find('.miniTip').miniTip({
        title: 'Advanced Keyword Syntax',
        event: 'click',
        content: "<p>+ signifies AND operation</p>"+
                 "<p>| signifies OR operation</p>"+
                 "<p>- negates a single token</p>"+
                 "\"\" wraps a number of tokens to signify a phrase for searching</p>"+
                 "<p>* at the end of a term signifies a prefix query</p>"+
                 "<p>( and ) signify precedence</p>"+
                 "<p>~N after a word signifies edit distance (fuzziness)</p>"+
                 "<p>~N after a phrase signifies slop amount (proximity searching)</p>"
    });

    // handle collect by for pivot tables
    var numericAttributes = $('#numeric-attributes-list');
    if(numericAttributes&&numericAttributes.attr('value')) {
        numericAttributes = JSON.parse(numericAttributes.attr('value'));
    }
    $topLevelElem.find('.collect-by-select').select2({
        minimumResultsForSearch: 10,
        width: "100%",
        templateSelection: select2SelectedFunction
    }).on('change', function(e) {
        var val = $(this).val();
        var $type = $(this).closest('.collect-container').find('select.collect-type');
        if(val) {
            var prevType = $type.val();
            // update type select from selected value
            var usePrevType = false;
            if(!numericAttributes.includes(val)) {
                if(prevType && ['Count','Cardinality'].includes(prevType)) {
                    usePrevType=true;
                }

                $type.empty();
                $type.append('<option value="Cardinality">Distinct Count</option>');
                $type.append('<option value="Count">Total Count</option>');
            } else {
                if(prevType && ['Sum','Average','Max','Min','StdDeviation','Variance'].includes(prevType)) {
                    usePrevType=true;
                }

                $type.empty();
                $type.append('<option value="Sum">Sum</option>');
                $type.append('<option value="Average">Average</option>');
                $type.append('<option value="Max">Max</option>');
                $type.append('<option value="Min">Min</option>');
                $type.append('<option value="StdDeviation">Standard Deviation</option>');
                $type.append('<option value="Variance">Variance</option>');
                $type.append('<option value="Count">Total Count</option>');
                $type.append('<option value="Cardinality">Distinct Count</option>');
            }
            $type.select2({
                minimumResultsForSearch: 10,
                width: "100%",
                templateSelection: select2SelectedFunction
            });
            if(usePrevType) {
                $type.val(prevType);
            }
            $type.trigger('select2.change');
        } else {
            $type.val(null).trigger('select2.change');
        }
    });

    setCollapsibleHeaders($topLevelElem.find(".collapsible-header"));

    var $nestedLists = $topLevelElem.find('.nested-form-list');
    $nestedLists.sortable();
    $nestedLists.disableSelection();

    $topLevelElem.find('input[type="checkbox"]').click(function(e) {
        if($(this).prop("checked")==true) {
            $(this).val('on');
        } else {
            $(this).val('off');
        }
        return true;
    });

    $topLevelElem.find(".datepicker" ).datepicker({
       changeMonth: true,
       changeYear: true,
       dateFormat: 'yy-mm-dd'
    });

    createTooltips($topLevelElem.find('[title]'), 'top');

};


var setupNavigationTabs = function() {
    var $navTabs = $('.nav-tabs li a');
    $navTabs.off('click');
    $navTabs.click(function(e) {
        e.preventDefault();
        e.stopPropagation()
        $(this).closest('ul').find('li').removeClass('active');
        $(this).tab('show');
    });
};

var handlePreviewAssetsAjax = function(chartId, group1, group2) {
    $.ajax({
        url: "/secure/preview",
        method: "POST",
        dataType: 'json',
        data: {
            chart_id: chartId,
            group1: group1,
            group2: group2
        },
        success: function(data) {
            if($.type(data)==='string') {
                data = $.parseJSON(data);
            }
            if(data.hasOwnProperty('html')) {
                var $box = $('<div class="lightbox"></div>');
                $box.hide();
                $(document.body).append($box);
                $box.append(data.html);
                var $preview = $box.find('#data-table-preview');
                var xButton = $('<span style="float: right; cursor: pointer;">X</span>');
                $preview.find('form').filter(':first').append(xButton);
                xButton.click(function(e) {
                    $box.trigger('click');
                });
                var tableId = 'main-preview-data-table';
                var $table = $('#'+tableId);
                $table.wrap('<div style="overflow-y: auto; width: 100%; height: 100%;"></div>');
                $table.parent().prepend($table.parent().prev());
                $table.parent().prepend($table.parent().prev());

                var boxFunc = function(e) {
                     e.stopPropagation();
                     $box.remove();
                     previewSelectionCache.clear();
                     setupNavigationTabs();
                     $('#results').find('table.table').each(function() {
                         var $this = $(this);
                         var $t = $this.data('dynatable');
                         $t.process();
                     });
                };
                $table.parent().parent().resizable({
                    handles: 's',
                    containment: $box,
                    start: function(e) {
                        e.stopPropagation();
                        $box.off('click');
                    },
                    resize: function(e) {
                        e.stopPropagation();
                    },
                    stop: function(e) {
                        e.stopPropagation();
                        setTimeout(function() {
                            $box.click(boxFunc);
                        },500);
                    }
                });
                $box.click(boxFunc);
                $box.children().css({
                    backgroundColor: 'white',
                    paddingTop: 75,
                    paddingLeft: 25,
                    paddingRight: 25,
                    paddingBottom: 5,
                    marginLeft: 0,
                    marginRight: 0,
                    cursor: 'default',
                    maxHeight: '100%',
                    borderBottomLeftRadius: 15,
                    borderBottomRightRadius: 15
                });
                $box.children().children().css({
                    maxHeight: '100%',
                    height: '80%',
                    paddingBottom: 20
                });
                $box.children().hide();
                $box.show();

                $('#data-table-preview').parent().click(function(e) {
                    e.stopPropagation();
                });
                if($table.find('thead th').length > 0) {
                   $table
                   .bind('dynatable:afterUpdate', function() {
                        $table.unwrap('.table-wrapper');
                        var $paginationTable = $('#dynatable-pagination-links-main-preview-data-table');
                        $paginationTable.click(function() {
                            setTimeout(function() {
                                $('#main-preview-data-table').parent().animate({
                                    scrollTop: 0
                                }, 300);
                            }, 100);
                        });
                        $paginationTable.find('li a[data-dynatable-page]').click(function(e) {
                            e.preventDefault();
                            var $dyna = $table.data('dynatable');
                            var val = parseInt($(this).attr('data-dynatable-page'), 10);
                            $dyna.paginationPage.set(val);
                            $dyna.process();
                        });
                        var all_rows_checked = update_table_function('#main-preview-data-table', previewSelectionCache);
                        $('#preview-data-table-select-all').prop('checked', all_rows_checked).trigger('change');

                        var $clone = $paginationTable.clone(false);
                        $clone.attr('id', 'dynatable-pagination-links-main-preview-data-table-clone');
                        var $holder = $('#preview-data-table-pagination-clone-holder');
                        $holder.empty();
                        $holder.append($clone);
                        var $original_links = $paginationTable.find('a');
                        var $cloned_links = $clone.find('a');
                        $cloned_links.each(function(index, element) {
                            var $cloned_link = $(element);
                            $cloned_link.attr('index', index.toString());
                            $cloned_link.click(function() {
                                var i = parseInt($cloned_link.attr('index'), 10);
                                var $link = $($original_links.get(i));
                                $link.click();
                            });
                        });
                        var $recordCountHolder = $('#dynatable-record-count-main-preview-data-table');
                        if($recordCountHolder.length>0 && $table.attr('data-total')) {
                            $recordCountHolder.text($recordCountHolder.text() + ' (Previewed from ' + $table.attr('data-total').toString() + ' records)');
                        }
                        $table.wrap('<div class="col-12 table-wrapper" style="overflow-x: auto; max-width:100%;"></div>');
                        return true;
                   }).dynatable({
                     dataset: {
                       ajax: true,
                       ajaxUrl: 'dataTable.json?tableId=preview',
                       ajaxOnLoad: true,
                       records: []
                     },
                     features: {
                        pushState: false,
                        paginate: true
                     }
                   });
                }
                $box.children().slideDown();
            }
        }
    });

};

var update_table_function = function(table,cache) {
   var $table = $(table);
   var $tableSelectionCounter = $table.parent().parent().find('.table-selection-counter');
   var $tableRows = $table.find('tbody tr');
   var num_rows = $tableRows.length;
   $tableRows = $tableRows.filter(function() {
       var $check = $(this).find('input.tableSelection');
       var checked = false;
       if(cache.has($check.val())) {
           $check.prop('checked', true);
           $check.trigger('change');
           checked = true;
       }
       $(this).dblclick(function() {
           $check.prop('checked', !$check.prop('checked'));
           $check.trigger('change');
       });
       $check.change(function() {
           // add to selection cache
           if($(this).prop('checked')) {
               cache.add($(this).val());
           } else {
               cache.delete($(this).val());
           }
           $tableSelectionCounter.text(cache.size.toString());
       });
       return checked;
   });
   var num_checked_rows = $tableRows.length;
   $tableSelectionCounter.text(cache.size.toString());
   return num_checked_rows>0 && num_checked_rows===num_rows;
};

var newContextMenu = function(e) {
    $('#context-menu-cntnr').remove();
    var menu = $(document.body).append('<div id="context-menu-cntnr">'+
                                    '<ul id="context-menu-items">'+
                                    '  <li value="delete">Remove this Point</li>'+
                                    '  <li value="edit">Edit this Point</li>'+
                                    '</ul>'+
                                '</div>');
    var $container = $('#context-menu-cntnr');
    $container.css("left",e.pageX);
    $container.css("top",e.pageY);
    function startFocusOut(){
        $(document).on("click",function(){
        $container.hide();
        $(document).off("click");
        });
    }
    $container.fadeIn(200, startFocusOut());
    return $container;
};

var isDrilldownChart = function(chart) {
    if(chart.hasOwnProperty('drilldownLevels') && chart.drilldownLevels.length>0) {
        return true;
    }
    for(var i = 0; i < chart.yAxis.length; i++) {
        if(chart.yAxis[i].hasOwnProperty('ddPoints') && ! $.isEmptyObject(chart.yAxis[i].ddPoints)) {
            return true;
        }
    }
    for(var i = 0; i < chart.xAxis.length; i++) {
        if(chart.xAxis[i].hasOwnProperty('ddPoints') && ! $.isEmptyObject(chart.xAxis[i].ddPoints)) {
            return true;
        }
    }
    for(var i = 0; i < chart.series.length; i++) {
        if(chart.series[i].hasOwnProperty('data') && chart.series[i].data.length>0 && chart.series[i].data[0].hasOwnProperty('drilldown')) {
            return true;
        }
    }
    return false;
};

$(document).ready(function() {


    $('.loader').show();

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
                 "<p>~N after a phrase signifies slop amount (proximity searching)</p>"
    });

    $('.miniTip2').miniTip({
        title: 'Acclaim Expert Search Syntax',
        event: 'click',
        maxW: '500px',
        content: "<div>Supports most functionality available in the Acclaim Expert Search form.</div><div>Supported Fields: </div>"+$('#acclaim-supported-fields').html()
    });


    var submitFormFunction = function(e,buttonClass,buttonText,buttonTextWhileSearching,formId,successFunction,canceledFunction,stopCancellable) {
         e.preventDefault();
         update_expert_query_text_area();

         var resultLimit = $('#main-options-limit').val();
         /*if(resultLimit) {
            if(resultLimit > 10000) {
                alert("Search for more than 10000 results may degrade performance.");
            }
         }*/

         var $form = $('#'+formId);
         if($form.prop('disabled')) {
            alert('Previous search is still in progress...');
            return;
         }
         $form.prop('disabled', true);
         var $button = $('.'+buttonClass);
         var url = $form.attr('action');
         $button.text(buttonTextWhileSearching);

         $(".attributeElement .attribute").not('.disabled').each(function() {
            var $this = $(this);
            var name = ".";
            while(name.includes(".")) {
                var $attributeElement = $this.parent().closest('.attributeElement');
                name = $attributeElement.attr('data-model');
                if(typeof name === 'undefined') return;
                var index = $attributeElement.parent().index();
                var $hiddenOrder = $('<input class="hidden-remove" type="hidden" name="order_'+ name +'" value="'+ index+'" />');
                $form.append($hiddenOrder);
                $this = $attributeElement;
            }
         });

         $.ajax({
           type: 'POST',
           dataType: 'json',
           url: url,
           data: $form.serialize(),
           complete: function(jqxhr,status) {
             if(!canceledFunction()) {
                $('#results-link').click();
             }
             stopCancellable();
             $button.prop('disabled',false).text(buttonText);
             $form.prop('disabled', false);
           },
           error: function(jqxhr,status,error) {
             if(!canceledFunction()) {
                 if(jqxhr.status==404 || jqxhr.status==502) {
                    alert("Unable to establish connection to platform. Try refreshing page. Error code: "+jqxhr.status.toString());
                 } else if(jqxhr.status==302) {
                    alert("Must sign in again. Try refreshing page. Error code: "+jqxhr.status.toString());
                 }
                 $('#results .content').html('<div style="color: red;">Server error during ajax request:'+error+'</div>');
             }
           },
           success: function(jqXHR,status,error) {
                if(!canceledFunction()) {
                    successFunction(jqXHR,status,error);
                }
           }
         });

         // remove orderings
         $form.find('.hidden-remove').remove();

         return false;
    };

    var successReportFromExcelOnly = function(data) {
        var urlPrefix = $('#url-prefix').attr('prefix');
        if(!urlPrefix) {
            urlPrefix = "/secure";
        }
        var $downloadForm = $('<form method="post" action="'+urlPrefix+'/excel_generation"></form>');
        $downloadForm.appendTo('body').submit().remove();
    };

    var successReportFrom = function(data) {
       var $tabs = $('#results .content');
       $tabs.empty();
       try {
           var $content = $(data.message).children();
           $tabs.each(function(i,e){
               $(this).html($content);
           });
       } catch(ex) {
           $tabs.each(function(i,e){
               $(this).html(data.message);
           });
       }
       setupNavigationTabs();

       if($('#main-data-table thead th').length > 0) {
           selectionCache.clear();

           $('#main-data-table')
           .bind('dynatable:afterUpdate', function() {
                $('#main-data-table').unwrap('.table-wrapper');
                var $paginationTable = $('#dynatable-pagination-links-main-data-table');
                $paginationTable.click(function() {
                    setTimeout(function() {
                        $('#main-container').animate({
                            scrollTop: 110
                        }, 300);
                    }, 100);
                });
                var all_rows_checked = update_table_function('#main-data-table', selectionCache);
                $('#data-table-select-all').prop('checked', all_rows_checked).trigger('change');
                var $clone = $paginationTable.clone(false);
                $clone.attr('id', 'dynatable-pagination-links-main-data-table-clone');
                var $holder = $('#data-table-pagination-clone-holder');
                $holder.empty();
                $holder.append($clone);
                var $original_links = $paginationTable.find('a');
                var $cloned_links = $clone.find('a');
                $cloned_links.each(function(index, element) {
                    var $cloned_link = $(element);
                    $cloned_link.attr('index', index.toString());
                    $cloned_link.click(function() {
                        var i = parseInt($cloned_link.attr('index'), 10);
                        var $link = $($original_links.get(i));
                        $link.click();
                    });
                });
                $('#main-data-table').wrap('<div class="col-12 table-wrapper" style="overflow-x: auto; max-width:100%;"></div>');
                return true;
           })
           .dynatable({
             dataset: {
               ajax: true,
               ajaxUrl: 'dataTable.json',
               ajaxOnLoad: true,
               records: []
             },
             features: {
                pushState: false
             }
           });

       } //else {
           //alert("Please include some attributes in the Attributes section.");
       //}

       setCollapsibleHeaders($('#results .collapsible-header'));

       if(data.hasOwnProperty('tableCnt')) {
            var tableCnt = data.tableCnt;
            for(var i = 0; i < tableCnt; i++) {
                var tableId = 'table-'.concat(String(i));
                var $table = $('#'+tableId);
                if($table.find('table thead th').length > 0) {
                   var $table = $table.find('table');
                   var headers = $table.find('thead th:not(:eq(0))');
                   $table
                   .bind('dynatable:afterUpdate', function($table, headers) {
                        return function() {
                            $table.find('tbody tr').each(function() {
                                var $tr = $(this);
                                $tr.find('td:not(:eq(0))')
                                .css('cursor', 'pointer')
                                .contextmenu(function(e) {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    var $td = $(this);
                                    var menu = newContextMenu(e);
                                    menu.find('ul').html('<li value="view">View Assets</li>');
                                    menu.find('li').click(function(e) {
                                        var index2 = $td.index() - 1;
                                        var group2 = null;
                                        var group1 = null;
                                        if(index2 >= 0) {
                                            group2 = headers.eq(index2).attr('data-dynatable-column');
                                            if(!group2) {
                                                group2 = headers.get(index2).nodeValue.trim();
                                            }
                                        }
                                        group1 = $td.parent().children().eq(0).text();
                                        handlePreviewAssetsAjax($table.parent().attr('id'), group1, group2);
                                    });
                                });
                            });
                        };
                   }($table, headers))
                   .dynatable({
                     dataset: {
                       ajax: true,
                       ajaxUrl: 'dataTable.json?tableId='.concat(String(i)),
                       ajaxOnLoad: true,
                       records: []
                     },
                     features: {
                        pushState: false
                     }
                   });

                }
            }
       }

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
                        var $currChart = $('<div id="'+ chartData.chartId+"-"+j.toString() +'"></div>');
                        var isStockChart = chartData.isStockCharts[j];
                        $currChart.appendTo($chartDiv);
                        var chartJson = chartData.charts[j];
                        chartJson['index']=j;
                        var chart;
                        if(isStockChart) {
                            // append data with data groupings
                            var buildStockChartCallback = function(chartData,j,chartJson) {
                                return Highcharts.stockChart(chartData.chartId+"-"+j.toString(), chartJson);
                            };

                            var updateDatagroupingByIndex = function(j,chartJson,originalSeriesData) {
                                var currentChart;
                                var currentBtnGroup = "year";
                                originalSeriesData = JSON.parse(JSON.stringify(originalSeriesData));
                                var updateDatagrouping = function(chartJson,units) {
                                    chartJson['plotOptions']['series']['dataGrouping'] = {
                                        enabled: true,
                                        force: true,
                                        approximation: 'sum',
                                        units: units
                                    };
                                    currentChart.destroy();
                                    chartJson['series'] = originalSeriesData;
                                    currentChart = buildStockChartCallback(chartData,j,chartJson);
                                    currentChart.redraw();
                                    return currentChart;
                                };
                                chartJson['rangeSelector'] = { enabled: false };
                                chartJson['plotOptions']['series']['dataGrouping'] = {
                                    enabled: true,
                                    force: true,
                                    approximation: 'sum',
                                    units: [['year',[1]]]
                                };
                                chartJson['chart']['events'] = {
                                    load: function() {
                                        var $btns = $('<div></div>');
                                        $btns.append('<label style="float: left; margin-bottom: 5px;">Group Dates By</label>');
                                        var $btnGroup = $('<div class="btn-group" style="margin-bottom: 5px;" role="group"></div>');
                                        $btns.append($btnGroup);

                                        var previous = chartJson['plotOptions']['series']['dataGrouping']['units'];

                                        var $dailyBtn = $('<button data-group="day" class="btn btn-sm btn-outline-secondary" type="button">Daily</button>');
                                        var $weeklyBtn = $('<button data-group="week" class="btn btn-sm btn-outline-secondary" type="button">Weekly</button>');
                                        var $monthlyBtn = $('<button data-group="month" class="btn btn-sm btn-outline-secondary" type="button">Monthly</button>');
                                        var $quarterlyBtn = $('<button data-group="quarter" class="btn btn-sm btn-outline-secondary" type="button">Quarterly</button>');
                                        var $yearlyBtn = $('<button data-group="year" class="btn btn-sm btn-outline-secondary" type="button">Yearly</button>');

                                        var updateFunction = function(btn,units) {
                                            currentBtnGroup = $(btn).attr("data-group");
                                            updateDatagrouping(chartJson,units);
                                        }

                                        $dailyBtn.click(function() {
                                            updateFunction(this,[['day',[1]]]);
                                        });
                                        $weeklyBtn.click(function() {
                                            updateFunction(this,[['week',[1]]]);
                                        });
                                        $monthlyBtn.click(function() {
                                            updateFunction(this,[['month',[1]]]);
                                        });
                                        $quarterlyBtn.click(function() {
                                            updateFunction(this,[['month',[3]]]);
                                        });
                                        $yearlyBtn.click(function() {
                                            updateFunction(this,[['year',[1]]]);
                                        });

                                        $btnGroup.append($dailyBtn);
                                        $btnGroup.append($weeklyBtn);
                                        $btnGroup.append($monthlyBtn);
                                        $btnGroup.append($quarterlyBtn);
                                        $btnGroup.append($yearlyBtn);

                                        $btnGroup.find('[data-group="'+currentBtnGroup+'"]').addClass('active');
                                        $(this.container).parent().prepend($btns)
                                    }
                                };

                                currentChart = buildStockChartCallback(chartData,j,chartJson);
                                return currentChart;
                            };
                            chart = updateDatagroupingByIndex(j,chartJson,chartJson.series);
                        } else {
                            // hack for word cloud chart
                            if(chartJson.hasOwnProperty('chart') && !chartJson['chart'].hasOwnProperty('type')) {
                                chartJson['chart']['type'] = 'wordcloud';
                                if(!chartJson.hasOwnProperty('plotOptions')) {
                                    chartJson['plotOptions'] = {};
                                }
                                if(!chartJson['plotOptions'].hasOwnProperty('wordcloud')) {
                                    chartJson['plotOptions']['wordcloud'] = {
                                        maxFontSize: 30,
                                        minFontSize: 10
                                    };
                                }
                            } else if (chartJson.hasOwnProperty('chart') && chartJson['chart']['type']==='heatmap') {
                                // hack for heatmap tooltips
                                chartJson['tooltip'] = {
                                    useHtml: true,
                                    formatter: function () {
                                        return this.series.yAxis.categories[this.point.y]+'<br>' + this.series.xAxis.categories[this.point.x] +'<br><b>'+ this.point.value +'</b>';
                                    }
                                };
                            }
                            if(!chartJson.hasOwnProperty('chart')) {
                                chartJson['chart'] = {};
                            }
                            var chartOpts = chartJson['chart'];
                            if(!chartOpts.hasOwnProperty('events')) {
                                chartOpts['events'] = {};
                            }
                            chartOpts['events']['drillup'] = function() {
                                this.redraw(true);
                            };

                            // add dbl click to axis labels
                            var axes = [];
                            if(chartJson.hasOwnProperty('xAxis')) {
                                axes.push(chartJson['xAxis']);
                            }
                            if(chartJson.hasOwnProperty('yAxis')) {
                                axes.push(chartJson['yAxis']);
                            }
                            for(var a = 0; a < axes.length; a++) {
                                var axis = axes[a];
                                for(var i = 0; i < axis.length; i++) {
                                    var axisOpts = axis[i];
                                    if (axisOpts.hasOwnProperty('type') && axisOpts['type']=='category') {
                                        if(!axisOpts.hasOwnProperty('labels')) {
                                            axisOpts['labels'] = {};
                                        }
                                        var labels = axisOpts['labels'];
                                        labels['events'] = {
                                            contextmenu: function(e) {
                                                if(!isDrilldownChart(this.chart)) {
                                                    var $target = $(e.currentTarget);
                                                    var axis = this;
                                                    var isYAxis = axis.axis.coll === 'yAxis';
                                                    var axisIndex = axis.value;
                                                    var $container = newContextMenu(e);
                                                    var $lis = $container.find('li');
                                                    $lis.on('click', function(e){
                                                        var $li = $(this);
                                                        $li.off('click');
                                                        var value = $li.attr('value');
                                                        if(value==='delete') {
                                                            if(axis.axis.categories !== true) {
                                                                var category = axis.axis.categories[axisIndex];
                                                                for(var i = 0; i < axis.chart.series.length; i++) {
                                                                    var datapoints = axis.chart.series[i].data.slice(0); // cloned
                                                                    for(var j = 0; j < datapoints.length; j++) {
                                                                        var point = datapoints[j];
                                                                        if(point) {
                                                                            if(isYAxis) {
                                                                                if(point.y!==axisIndex) {
                                                                                    if (point.y>axisIndex) {
                                                                                        point.update({y : point.y-1, x: point.x, value: point.value}, false);
                                                                                    }
                                                                                } else {
                                                                                    point.remove(false);
                                                                                }
                                                                            } else {
                                                                                if(point.hasOwnProperty('value')) {
                                                                                    // heat map
                                                                                    if(point.x!==axisIndex) {
                                                                                        if (point.x>axisIndex) {
                                                                                            point.update({x : point.x-1, y: point.y, value: point.value}, false);
                                                                                        }
                                                                                    } else {
                                                                                        point.remove(false);
                                                                                    }
                                                                                } else { // regular
                                                                                    if(point.name===category) {
                                                                                        point.remove(false);
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                axis.axis.categories.splice(axisIndex, 1);
                                                                axis.axis.setCategories(axis.axis.categories);
                                                                axis.axis.isDirty=true;
                                                                axis.chart.redraw(true);
                                                            }

                                                        } else if(value==='edit') {
                                                            e.preventDefault();
                                                            e.stopPropagation();
                                                            var value = $target.text();
                                                            var oldHtml = $li.html();
                                                            $li.html('<hr /><form><input class="form-control" type="text" /><button>Update</button></form><hr />');
                                                            $li.addClass('nohover');
                                                            var $input = $li.find('input');
                                                            $input.val(value);
                                                            var $form = $li.find('form');
                                                            $(document).on("click",function(){
                                                                $li.html(oldHtml);
                                                                $li.removeClass('nohover');
                                                                $container.remove();
                                                                $(document).off("click");
                                                            });
                                                            $form.on('click', function(e) {
                                                                e.stopPropagation();
                                                            });
                                                            $form.on('submit', function(e) {
                                                                e.preventDefault();
                                                                $form.off('submit');
                                                                var userVal = $input.val();
                                                                if(axis.axis.categories !== true) {
                                                                    var category = axis.axis.categories[axisIndex];
                                                                    axis.axis.categories[axisIndex] = userVal;
                                                                    axis.axis.update({categories: axis.axis.categories});
                                                                    for(var i = 0; i < axis.chart.series.length; i++) {
                                                                        var datapoints = axis.chart.series[i].data.slice(0); // create a clone
                                                                        for(var j = 0; j < datapoints.length; j++) {
                                                                            var point = datapoints[j];
                                                                            if(point) {
                                                                                if(!isYAxis) {
                                                                                    if(point.hasOwnProperty('name') && point.name===category) {
                                                                                        point.update({name: userVal}, false);
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                    axis.chart.redraw(true);
                                                                }
                                                                $(document).trigger('click');
                                                            });
                                                        }
                                                    });
                                                }
                                            }
                                        };
                                    }
                                }
                            }

                            // add dbl click to buttons
                            if(!chartJson.hasOwnProperty('plotOptions')) {
                                chartJson['plotOptions'] = {};
                            }
                            if(!chartJson['plotOptions'].hasOwnProperty('series')) {
                                chartJson['plotOptions']['series'] = {};
                            }
                            if(!chartJson['plotOptions']['series'].hasOwnProperty('point')) {
                                chartJson['plotOptions']['series']['point'] = {};
                            }
                            if(!chartJson['plotOptions']['series'].hasOwnProperty('dataLabels')) {
                                chartJson['plotOptions']['series']['dataLabels'] = {};
                            }
                            if(!chartJson.hasOwnProperty('chart')) {
                                chartJson['chart'] = {};
                            }
                            if(!chartJson['chart'].hasOwnProperty('events')) {
                                chartJson['chart']['events'] = {};
                            }
                            if(!chartJson['chart']['events'].hasOwnProperty('load')) {
                                if(chartJson['chart']['type']==='pie' && chartJson['series'].length>1) {
                                    chartJson['chart']['events']['load'] = function() {
                                        var series1 = this.series[0];
                                        var series2 = this.series[1];
                                        var data1 = series1.data;
                                        var data2 = series2.data;
                                        for(var p = 0; p < data1.length; p++) {
                                            var point = data1[p];
                                            point.update({
                                                original_1: null,
                                                original_2: point.name
                                            }, false);
                                        }
                                        var currentAmount = 0;
                                        var currentIdx = 0;
                                        var totalSoFar = data1[0].y;
                                        for(var p = 0; p < data2.length; p++) {
                                            var point = data2[p];
                                            currentAmount += point.y;
                                            if(currentAmount > totalSoFar && data1.length > currentIdx + 1) {
                                                currentIdx = currentIdx + 1;
                                                totalSoFar += data1[currentIdx].y;
                                            }
                                            point.update({
                                                original_1: point.name,
                                                original_2: data1[currentIdx].name
                                            }, false);
                                        }

                                    };

                                } else {
                                    chartJson['chart']['events']['load'] = function() {
                                        for(var i = 0; i < this.series.length; i++) {
                                            var data = this.series[i].data;
                                            for(var p = 0; p < data.length; p++) {
                                                var point = data[p];
                                                if(point.hasOwnProperty('value')) {
                                                    point.update({
                                                        original_1: point.series.chart.xAxis[0].categories[point.x],
                                                        original_2: point.series.chart.yAxis[0].categories[point.y]
                                                    }, false);
                                                } else {
                                                    point.update({
                                                        original_1: point.name,
                                                        original_2: point.series.name
                                                    }, false);
                                                }
                                            }
                                        }
                                    };
                                }
                            }
                            if(!chartJson['chart']['events'].hasOwnProperty('redraw')) {
                                // hack to allow clicking on data label instance of data point only
                                /*chartJson['chart']['events']['redraw'] = function() {
                                    for(var i = 0; i < this.series.length; i++) {
                                        var data = this.series[i].data;
                                        for(var p = 0; p < data.length; p++) {
                                            var point = data[p];
                                            if(point.hasOwnProperty('dataLabel')) {
                                                var $dataLabel = $(point.dataLabel.text.element);
                                                $dataLabel.contextmenu(function(point) { return function(e) {
                                                    e.preventDefault();
                                                    e.stopPropagation();
                                                    point.firePointEvent('contextmenu', e);
                                                }}(point));
                                            }
                                        }
                                    }
                                }*/
                            }
                            var clickEvents = {
                                contextmenu: function(e) {
                                    if(this.hasOwnProperty('series') && !isDrilldownChart(this.series.chart)) {
                                        var point = this;
                                        var chartId = $(point.series.chart.renderTo).attr('id');
                                        var value = this.options.name;
                                        var seriesIndex = this.series.index;
                                        var pointIndex = this.index;
                                        var $container = newContextMenu(e);
                                        $container.find('ul').append('<li value="view" >View Assets</li>')
                                        var $lis = $container.find('li');
                                        $lis.on('click', function(e){
                                            var $li = $(this);
                                            $li.off('click');
                                            var value = $li.attr('value');
                                            var group1 = null;
                                            var group2 = null;
                                            if (value === 'view') {
                                                group1 = point.original_1;
                                                group2 = point.original_2;
                                                handlePreviewAssetsAjax(chartId, group1, group2);

                                            } else if (value==='delete') {
                                                point.remove();

                                            } else if (value==='edit') {
                                                e.preventDefault();
                                                e.stopPropagation();
                                                var value = null;
                                                if(point.hasOwnProperty('value')) {
                                                    value = point.value;
                                                } else {
                                                    value = point.y;
                                                }
                                                var oldHtml = $li.html();
                                                var isPieChart = point.series.type.toLowerCase()==='pie';
                                                var $form = null;
                                                if(isPieChart) {
                                                    $form = $('<form><input class="form-control" type="text" /><input class="form-control" type="text" /><button>Update</button></form>');
                                                } else {
                                                    $form = $('<form><input class="form-control" type="text" /><button>Update</button></form>');
                                                }
                                                $li.html('<hr />');
                                                $li.addClass('nohover');
                                                $li.append($form);
                                                var $input = $li.find('input');
                                                var $inputY = null;
                                                if($input.length==2) {
                                                    $inputY = $($input.get(1));
                                                    $input = $($input.get(0));
                                                    $inputY.val(point.y);
                                                    $input.val(point.name);
                                                } else {
                                                    $input.val(value);
                                                }
                                                $(document).on("click",function(){
                                                    $li.html(oldHtml);
                                                    $li.removeClass('nohover');
                                                    $container.hide();
                                                    $(document).off("click");
                                                    $lis.off('click');
                                                });
                                                $form.on('click', function(e) {
                                                    e.stopPropagation();
                                                });
                                                $form.on('submit', function(e) {
                                                    e.preventDefault();
                                                    var userVal = $input.val();
                                                    var userY = null;
                                                    if(isPieChart) {
                                                        userY = $inputY.val();
                                                    }
                                                    if(point.hasOwnProperty('value')) {
                                                        userVal = Number(userVal);
                                                        point.update({value: userVal});
                                                    } else {
                                                        if(isPieChart) {
                                                            userY = Number(userY);
                                                            point.update({name: userVal, y: userY});
                                                        } else {
                                                            userVal = Number(userVal);
                                                            point.update({y: userVal});
                                                        }
                                                    }
                                                    $(document).trigger('click');
                                                });


                                            }
                                        });
                                    }
                                }
                            };
                            if(chartJson['chart']['type']=='wordcloud') {
                                chartJson['plotOptions']['series']['point']['events']  = {
                                    events: {
                                        click: function(e) {
                                            return clickEvents['contextmenu'](e);
                                        }
                                    }
                                };

                            } else {
                                chartJson['plotOptions']['series']['point']['events'] = clickEvents;
                            }
                            //chartJson['plotOptions']['series']['dataLabels']['events'] = {
                            //    contextmenu: function(e) {
                            //        e.preventDefault();
                            //    }
                            //};
                            chart = Highcharts.chart(chartData.chartId+"-"+j.toString(), chartJson);
                        }
                        chart.redraw();
                      }
                   }
                 }
               });
             }
           }
         } catch (err) {
           $('#results .content').html("<div style='color:red;'>JavaScript error occured while rendering charts: " + err.message + '</div>');
         }
       }
    };

    var cancelReportFunction = function() {
        $.ajax({
            type: "POST",
            dataType: "json",
            url: "/cancel_report",
            complete: function() {

            }
        });
    };

    $('#generate-reports-form').submit(function(e) {
        e.preventDefault();
        $(this).find('#only-excel-hidden-input').val(false);
        var buttonClass = "generate-reports-form-button";
        var buttonText = "Generate Report";
        var buttonTextWhileSearching = "Generating... (Click to Cancel)";
        var formId = $(this).attr('id');
        $('#results .content').html(''); // clears results div
        var $this = $(this);
        var canceledFunction = function() {
            return $this.prop('canceled');
        };
        var stopCancellable = function() {
            $('.'+buttonClass).prop('disabled', false);
            $('.'+buttonClass).prop('canceled', false);
        };
        return submitFormFunction(e,buttonClass,buttonText,buttonTextWhileSearching,formId,successReportFrom,canceledFunction,stopCancellable);
    });
    $('.generate-reports-form-button').click(function(e) {
        e.preventDefault();
        if($(this).prop('disabled')) {
            // cancel!
            $('.generate-reports-form-button').prop('canceled', true);
            cancelReportFunction();
        } else {
            $('.generate-reports-form-button').prop('disabled', true);
            $('#generate-reports-form').submit();
        }
    });
    $('.download-to-excel-button').click(function(e) {
        e.preventDefault();
        var buttonClass = "download-to-excel-button";
        var buttonText = "Download to CSV";
        var buttonTextWhileSearching = "Downloading... (Click to Cancel)";
        var formId = 'generate-reports-form';
        if($(this).prop('disabled')) {
            // cancel!
            $('.'+buttonClass).prop('canceled', true);
            cancelReportFunction();

        } else {
            var $this = $(this);
            var canceledFunction = function() {
                return $this.prop('canceled');
            };
            var stopCancellable = function() {
                $('.'+buttonClass).prop('disabled', false);
                $('.'+buttonClass).prop('canceled', false);
            };
            $('#generate-reports-form').find('#only-excel-hidden-input').val(true);
            $('.'+buttonClass).prop('disabled', true);
            return submitFormFunction(e,buttonClass,buttonText,buttonTextWhileSearching,formId,successReportFromExcelOnly,canceledFunction,stopCancellable);
        }
    });

    $('#update-default-attributes-form').click(function(e) {
        e.preventDefault();
        var name = 'default';
        var urlPrefix = $('#url-prefix').attr('prefix');
        var extract_to_usergroup = false;
        var $extractUG = $('#extract_to_usergroup');
        if($extractUG.length>0) {
            extract_to_usergroup = $extractUG.is(':checked');
        };
        if(!urlPrefix) {
            urlPrefix = "/secure";
        }
        var postSaveCallback = function() {
            window.location.href = urlPrefix+'/home'
        };
        var callback = function(data) {
            saveJSNodeFunction(null,null,name,true,data,'template',true,true,postSaveCallback,false);
        };
        return templateDataFunction(null,null,name,true,callback,null,extract_to_usergroup);
    });

    setupNestedFilterSelects($('select.nested-filter-select'), $(document));

    $('.sidebar .nav-item .btn').click(function(e){
        $('.sidebar .nav-item .btn').removeClass('active');
        $(this).addClass('active');
    });


    $('#main-content-id').addClass('show');
    $('#sidebar-jstree-wrapper').show();

    /*$(document).uitooltip({
        content: function() {
            return $(this).attr('title');
        },
        //html: true,
        show: {
            delay: 400,
            duration: 200
        }
    });
    $(document).click(function() {
        $('.ui-tooltip').remove();
    }); */


    $('.custom-menu').click(function(e) {
        e.preventDefault();
        e.stopPropagation();
        var mainMenu = $('#main-menu');
        mainMenu.slideToggle();
    });


    if(document.getElementById('acclaim_expert_filter')) {
        editor = CodeMirror.fromTextArea(document.getElementById('acclaim_expert_filter'), {
            lineNumbers: false,
            matchBrackets: true,
            autoCloseBrackets: true,
            lineWrapping: true
        });
    }

    $('#change_user_group_label').click(function(e) {
        e.stopPropagation();
    });

    var saveTemplateFormHelper = function(containerSelector,itemSelector,dataMap,dataKey) {
        var tmpData = {};
        $(containerSelector+" "+itemSelector).find('textarea,input,select,div.attribute').each(function(i,e) {
            var $elem = $(this);
            var id = $elem.attr('id');
            if(id && ! ($elem.prop('disabled') || $elem.hasClass('disabled'))) {
                if(! (id in tmpData)) {
                    if($elem.attr('type')==='checkbox') {
                        if($elem.prop("checked")==true) {
                            $elem.val('on');
                        } else {
                            $elem.val('off');
                        }
                    }
                    tmpData[id]=$elem.val();
                    if($elem.is('select.nested-filter-select') || $elem.is('div.attribute')) {
                        tmpData["order_"+id]=i;
                    }
                }
            }
        });
        var json = JSON.stringify(tmpData);
        dataMap[dataKey] = json;
    };

    var templateDataFunction = function(tree,node,name,deletable,callback,obj,extract_to_usergroup) {
        update_expert_query_text_area();

        var preData = {};
        if(extract_to_usergroup) {
            preData["extract_to_usergroup"] = true;
        }
        preData["name"]=name;
        saveTemplateFormHelper("#searchOptionsForm",".attributeElement",preData,"searchOptionsMap");
        saveTemplateFormHelper("#attributesForm",".attributeElement",preData,"attributesMap");
        saveTemplateFormHelper("#filtersForm",".attributeElement",preData,"filtersMap");
        saveTemplateFormHelper("#chartsForm",".attributeElement",preData,"chartsMap");
        saveTemplateFormHelper("#highlightForm",".attributeElement",preData,"highlightMap");
        preData["deletable"] = deletable;

        if(node!==null) {
            if(node.hasOwnProperty('data') && node.data.hasOwnProperty('file')) {
                preData["file"] = node.data.file;
            }
            if(node.hasOwnProperty('data') && node.data.hasOwnProperty('user')) {
                preData["user"] = node.data.user;
            }
            preData["parentDirs"] = [];
            var nodeData = node;
            while(typeof nodeData.text !== 'undefined') {
                if(nodeData.type==='folder') {
                    preData["parentDirs"].unshift(nodeData.text);
                }
                var currId = nodeData.parent;
                nodeData = tree.get_node(currId);
            }
        }
        callback(preData);
    };


    var showTemplateFormHelper = function(formSelector,dataMap,mainSelectID) {
        var doFunction = function() {
            $.each(dataMap,function(id,value) {
                if(!id.startsWith("order_")) {
                     var order = null;
                     if(dataMap.hasOwnProperty("order_"+id)) {
                         order = dataMap["order_"+id];
                     }

                     var $elem = $('#'+id);
                     var $draggable = $elem.closest(".attributeElement");
                     $draggable.parent().show();
                     if(order!==null&& ($elem.is('select.nested-filter-select') || $elem.is('div.attribute'))) {
                         $draggable.parent().attr("sort-order",order);
                     }
                     if($elem.attr('type')==="checkbox") {
                         $elem.prop('checked',value==='on');
                     }
                     if($elem.hasClass('multiselect-ajax')) {
                         var labeledValues = [];
                         var needToFindLabels = [];
                         for(var i = 0; i < value.length; i++) {
                             var val = value[i];
                             if($elem.find('option[value="'+val+'"]').length) {
                                 labeledValues.push(val); // good to go
                             } else {
                                 needToFindLabels.push(val); // not good :(
                             }
                         }
                         $elem.val(labeledValues).trigger('change');
                         if(needToFindLabels.length > 0) {
                           // get label
                           $.ajax({
                             type: "GET",
                             url: $elem.attr("data-url"),
                             data: {
                               get_label_for: needToFindLabels
                             },
                             success: function(data) {
                               if(data.hasOwnProperty('labels')&&data.hasOwnProperty('values')) {
                                 for(var i = 0; i < data.labels.length; i++) {
                                   var newVal = new Option(data.labels[i],data.values[i],true,true);
                                   $elem.append(newVal);
                                 }
                               }
                             },
                             error: function(jqXHR, exception) {
                               for(var i = 0; i < needToFindLabels.length; i++) {
                                 var val = needToFindLabels[i];
                                 var newVal = new Option(val,val,true,true);
                                 $elem.append(newVal);
                               }
                             },
                             dataType: "json"
                           });
                         }

                     } else {
                         $elem.val(value);
                     }
                     $elem.not('select.nested-filter-select').trigger('change');
                 }
            });
            $(formSelector+' select.nested-filter-select').filter(':first').trigger('change', [true]);
            update_expert_query_display();
        };
        // pull any attrs necessary from server
        if(mainSelectID) {
            var $formList = $(mainSelectID).parent().next();
            // do it two times to get nested stuff
            var ids_to_fetch = [];
            for(var id in dataMap) {
                if(dataMap.hasOwnProperty(id)) {
                    if(!id.startsWith("order_")) {
                        var $elem = $('#'+id);
                        if($elem.length==0 && !formIDRequestedCache.has(id)) {
                            // get option from server
                            ids_to_fetch.push(id);
                        }
                    }
                }
            }
            if(ids_to_fetch.length>0) {
                // try to get it from the server
                $.when($.ajax({
                    type: "POST",
                    url: '/form_elem_by_id',
                    data: {
                        ids: ids_to_fetch
                    },
                    success: function(data) {
                        if(data && data.hasOwnProperty('results')) {
                            var $new = $(data.results);
                            $formList.append($new);
                            for(var i = 0; i < data.ids.length; i++) {
                                formIDRequestedCache.add(data.ids[i]);
                            }
                            $new.show();
                            var $newFilters = $new.find('.nested-filter-select');
                            setupNestedFilterSelects($newFilters, $new);
                        }
                    },
                    dataType: "json"
                })).done(function() {
                    doFunction();
                    setTimeout(function() {
                        doFunction();
                    }, 200);
                    setTimeout(function() {
                        doFunction();
                    }, 500);
                    setTimeout(function() {
                        doFunction();
                    }, 1000);

                });
            } else {
                doFunction();
            }
        } else {
            doFunction();
        }
    };

    var showTemplateFunction = function(data,tree,node){
        if(node!==null){ resetSearchForm(); }
        var $loaders = $('.loader');
        $loaders.show();
        $('#data-tab-link').click();
        $('#filters-link').click();
        if(data===null) {
            alert("Error finding template.");
            $loaders.hide();
        } else if(data.hasOwnProperty('searchoptionsmap')) { // data came from li node
            showTemplateFormHelper("#searchOptionsForm",data["searchoptionsmap"],null);
            showTemplateFormHelper("#attributesForm",data["attributesmap"],"#multiselect-nested-filter-select-attributes");
            showTemplateFormHelper("#filtersForm",data["filtersmap"], '#multiselect-nested-filter-select-attributesNested_filter');
            showTemplateFormHelper("#chartsForm",data["chartsmap"],'#multiselect-nested-filter-select-chartModels');
            try {
                showTemplateFormHelper("#highlightForm",data["highlightmap"],null);
            } catch(err) {

            }
            setTimeout(function() { $loaders.hide(); }, 250);
        } else if(data.hasOwnProperty('searchOptionsMap')) { // data came from newly added node
            showTemplateFormHelper("#searchOptionsForm",$.parseJSON(data["searchOptionsMap"]),null);
            showTemplateFormHelper("#attributesForm",$.parseJSON(data["attributesMap"]),"#multiselect-nested-filter-select-attributes");
            showTemplateFormHelper("#filtersForm",$.parseJSON(data["filtersMap"]),'#multiselect-nested-filter-select-attributesNested_filter');
            showTemplateFormHelper("#chartsForm",$.parseJSON(data["chartsMap"]),'#multiselect-nested-filter-select-chartModels');
            try {
                showTemplateFormHelper("#highlightForm",data["highlightMap"],null);
            } catch(err) {

            }
            setTimeout(function() { $loaders.hide(); }, 250);

        } else if(data.hasOwnProperty('file')) {
            // need to get data
            var defaultFile = node === null;
            var shared = false;
            var preset = false;
            if(node!==null) {
                preset = !node.data.deletable;
                var nodeData = node;
                var parents = [];
                while(typeof nodeData.text !== 'undefined') {
                    if(nodeData.type==='folder') {
                        parents.unshift(nodeData.text);
                    }
                    var currId = nodeData.parent;
                    nodeData = tree.get_node(currId);
                }
                shared = parents.length > 0 && parents[0].startsWith("Shared");
            }
            var urlPrefix = $('#url-prefix').attr('prefix');
            if(!urlPrefix) {
                urlPrefix = "/secure";
            }
            $.ajax({
                type: "POST",
                url: urlPrefix+'/get_template',
                data: {
                    file: data.file,
                    shared: shared,
                    preset: preset,
                    defaultFile: defaultFile
                },
                success: function(data) {
                    showTemplateFunction(data,null,null);
                },
                dataType: "json"
            });
        }
        update_expert_query_display();
        return false;
    };

    $('#synonym-test-word,#synonym-test-max,#synonym-test-word').keyup(function(e) {
        e.stopPropagation();
        e.preventDefault();
        if (e.keyCode === 13) {
           $('#synonym-finder-button').trigger('click');
        }
    });

    $('#synonym-finder-button').click(function(e) {
        e.preventDefault();
        e.stopPropagation();
        var $button = $(this);
        if($button.hasClass('disabled')) {
            return false;
        }
        $button.addClass('disabled');
        var $text = $button.text();
        $button.text('Finding...');
        var $container = $('#synonymForm');
        var words = $('#synonym-test-word').val();
        var max_synonyms = $('#synonym-test-max').val();
        var min_similarity = $('#synonym-test-min').val();
        $('#miniTip').hide();

        $.ajax({
            dataType: 'json',
            type: 'POST',
            url: $container.attr('data-url'),
            success: function(data) {
                $('#synonym-test-results').html(data.results);
            },
            data: {
                min_similarity: min_similarity,
                max_synonyms: max_synonyms,
                words: words
            },
            error: function() {
                $('#synonym-test-results').html('An unknown error occurred.');
            },
            complete: function() {
                $button.removeClass('disabled');
                $button.text($text);
            }
        });
    })


    // prevent bug with text editor and sortable lists
    $('.CodeMirror-wrap').parent().mousedown(function(e) {
        e.stopPropagation();
    });

    resetSearchForm();
    showTemplateFunction({file: 'default'},null,null);


    setupJSTree("#templates-tree",showTemplateFunction,"template",[templateDataFunction],["From Current Form"]);
    setupJSTree("#datasets-tree",showDatasetFunction,"dataset",[selectionDatasetDataFunction,lastGeneratedDatasetDataFunction,assetListDatasetDataFunction,emptyDatasetDataFunction],["From Selection", "From Last Generated Report", "From Asset List", "Empty Dataset"]);

    createTooltips($('[title]'), 'top');

    // defaults
    $.notify.defaults({
        position: 'bottom left',
        globalPosition: 'bottom left',
        elementPosition: 'bottom left',
        autoHideDelay: 3000
    });

    setupNavigationTabs();

});

var notify_success = function(text) {
    $.notify(text, 'success');
};

var notify_error = function(text) {
    $.notify(text, 'error');
};

var resetSearchForm = function() {
    $('.attributeElement').not('.draggable').each(function() { $(this).find('select.nested-filter-select').filter(':first').val(null).trigger('change',[true]); });
    $('div.attribute').addClass("disabled");
    $('#results .content').html('');
};

var findByValue = function(inputs, value) {
    for(var i = 0; i < inputs.length; i++) {
        if(inputs[i].value == value) return inputs[i];
    }
    return null;
};


var setCollapsibleHeaders = function($selector) {
    $selector.click(function () {
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

var showDatasetFunction = function(data,tree,node){
    // need to get data
    $('#data-tab-link').click();
    $('#filters-link').click();
    var nodeData = node;
    var parents = [];
    while(typeof nodeData.text !== 'undefined') {
        if(nodeData.type==='folder') {
            parents.unshift(nodeData.text);
        }
        var currId = nodeData.parent;
        nodeData = tree.get_node(currId);
    }
    var shared = parents.length > 0 && parents[0].startsWith("Shared");
    $('#filters-row .attributeElement').not('.draggable').each(function() { $(this).find('select.nested-filter-select').filter(':first').val(null).trigger('change',[true]); });
    $('#filters-row div.attribute').addClass("disabled");
    var $datasetInput = $('#multiselect-multiselect-datasetNameInclude_filter');
    var $filter = $('#multiselect-nested-filter-select-attributesNested_filter');
    $filter.val([$datasetInput.attr('name')]).trigger('change');
    var name = data.file+"_"+data.user;
    if(!$datasetInput.find('option[value="'+name+'"]').length) {
        var newVal = new Option(node.text,name,true,true);
        $datasetInput.append(newVal);
    }
    $datasetInput.val(name).trigger('change');
    return false;
};

var showMultipleDatasetFunction = function(data,tree,node){
    // need to get data
    $('#data-tab-link').click();
    $('#filters-link').click();
    var nodeData = node;
    var parents = [];
    while(typeof nodeData.text !== 'undefined') {
        if(nodeData.type==='folder') {
            parents.unshift(nodeData.text);
        }
        var currId = nodeData.parent;
        nodeData = tree.get_node(currId);
    }
    var shared = parents.length > 0 && parents[0].startsWith("Shared");
    $('#filters-row .attributeElement').not('.draggable').each(function() { $(this).find('select.nested-filter-select').filter(':first').val(null).trigger('change',[true]); });
    $('#filters-row div.attribute').addClass("disabled");
    var $datasetInput = $('#multiselect-multiselect-datasetNameInclude_filter');
    var $filter = $('#multiselect-nested-filter-select-attributesNested_filter');
    $filter.val([$datasetInput.attr('name')]).trigger('change');
    var names = [];
    if(node.children) {
        if(node.children.length > 0) {
            for(var i = 0; i < node.children.length; i++) {
                var child = node.children[i];
                child = tree.get_node(child);
                    if(child.type==='file') {
                    var file = child.data.file;
                    var user = child.data.user;
                    var name = file + "_" + user;
                    names.push(name);
                    if(!$datasetInput.find('option[value="'+name+'"]').length) {
                        var newVal = new Option(child.text,name,true,true);
                        $datasetInput.append(newVal);
                    }
                }
            }
        }
    }
    $datasetInput.val(names).trigger('change');
    return false;
};

var addMultipleDatasetFunction = function(data,tree,node){
    $('#data-tab-link').click();
    $('#filters-link').click();
    var $datasetInput = $('#multiselect-multiselect-datasetNameInclude_filter');

    // need to get data
    var names = $datasetInput.val();
    var nodeData = node;
    var parents = [];
    while(typeof nodeData.text !== 'undefined') {
        if(nodeData.type==='folder') {
            parents.unshift(nodeData.text);
        }
        var currId = nodeData.parent;
        nodeData = tree.get_node(currId);
    }
    var shared = parents.length > 0 && parents[0].startsWith("Shared");

    var $filter = $('#multiselect-nested-filter-select-attributesNested_filter');
    var prevFilters = $filter.val();
    if(prevFilters===null) {
        prevFilters = [];
    }
    if(!prevFilters.includes($datasetInput.attr('name'))) {
        prevFilters.push($datasetInput.attr('name'));
    }
    $filter.val(prevFilters).trigger('change');
    if(node.children) {
        if(node.children.length > 0) {
            for(var i = 0; i < node.children.length; i++) {
                var child = node.children[i];
                child = tree.get_node(child);
                    if(child.type==='file') {
                    var file = child.data.file;
                    var user = child.data.user;
                    var name = file + "_" + user;
                    if(!names.includes(name)) {
                        names.push(name);
                        if(!$datasetInput.find('option[value="'+name+'"]').length) {
                            var newVal = new Option(child.text,name,true,true);
                            $datasetInput.append(newVal);
                        }
                    }
                }
            }
        }
    }
    $datasetInput.val(names).trigger('change');
    return false;
};

var select2SelectedFunction = function(item) {
  var $option = $(item.element);
  var $optGroup = $option.parent();
  if($optGroup.is("optgroup") && $optGroup.get(0).label!==item.text) {
      return item.text + " of "+$optGroup.get(0).label;
  } else {
      return item.text;
  }
}

var renameJSNodeFunction = function(tree,node,newName,file,node_type){
     var nodeData = tree.get_node(node.parent);
     var parents = [];
     while(typeof nodeData.text !== 'undefined') {
         if(nodeData.type==='folder') {
            parents.unshift(nodeData.text);
         }
         var currId = nodeData.parent;
         nodeData = tree.get_node(currId);
     }
     var urlPrefix = $('#url-prefix').attr('prefix');
     if(!urlPrefix) {
         urlPrefix = "/secure";
     }
     $.ajax({
         type: "POST",
         url: urlPrefix+'/rename_'+node_type,
         data: {
             name: newName,
             parentDirs: parents,
             file: file
         },
         success: function(data) {
             var refresh = false;
             if(node_type==='dataset' && data.hasOwnProperty('asset_count')) {
                 // update asset counts
                 node.data['assetcount'] = data['asset_count'];
                 refresh = true;
             }
             if(refresh) {
                 tree.redraw(true);
             }
         },
         error: function(jqxhr,status,error) {
             if(jqxhr.status==404) {
                alert("Unable to establish connection to platform. Try refreshing page.");
             }
         },
         dataType: "json"
     });
     return false;
};

var removeJSNodeFunction = function(tree,node,file,node_type){
     var nodeData = node;
     var parents = [];
     while(typeof nodeData.text !== 'undefined') {
         if(nodeData.type==='folder') {
             parents.unshift(nodeData.text);
         }
         var currId = nodeData.parent;
         nodeData = tree.get_node(currId);
     }
     var shared = parents.length > 0 && parents[0].startsWith("Shared");
     var urlPrefix = $('#url-prefix').attr('prefix');
     if(!urlPrefix) {
         urlPrefix = "/secure";
     }
     $.ajax({
         type: "POST",
         url: urlPrefix+'/delete_'+node_type,
         data: {
             path_to_remove: file,
             shared: shared
         },
         success: function(data) {
             tree.delete_node(node);
         },
         error: function(jqxhr,status,error) {
             if(jqxhr.status==404) {
                alert("Unable to establish connection to platform. Try refreshing page.");
             }
         },
         dataType: "json"
     });
     return false;
};

var lastGeneratedDatasetDataFunction = function(tree,node,name,deletable,callback,obj) {
    var preData = {};
    preData["name"]=name;
    preData["createDataset"]= true;
    preData["parentDirs"] = [];
    preData["deletable"] = deletable;
    if(node.hasOwnProperty('data') && node.data.hasOwnProperty('file')) {
        preData["file"] = node.data.file;
    }
    if(node.hasOwnProperty('data') && node.data.hasOwnProperty('user')) {
        preData["user"] = node.data.user;
    }
    var nodeData = node;
    while(typeof nodeData.text !== 'undefined') {
        if(nodeData.type==='folder') {
            preData["parentDirs"].unshift(nodeData.text);
        }
        var currId = nodeData.parent;
        nodeData = tree.get_node(currId);
    }
    callback(preData);
};

var emptyDatasetDataFunction = function(tree,node,name,deletable,callback,obj) {
    var preData = {};
    preData["name"]=name;
    preData["createDataset"] = true;
    preData["emptyDataset"] = true;
    preData["parentDirs"] = [];
    preData["deletable"] = deletable;
    if(node.hasOwnProperty('data') && node.data.hasOwnProperty('file')) {
        preData["file"] = node.data.file;
    }
    if(node.hasOwnProperty('data') && node.data.hasOwnProperty('user')) {
        preData["user"] = node.data.user;
    }
    var nodeData = node;
    while(typeof nodeData.text !== 'undefined') {
        if(nodeData.type==='folder') {
            preData["parentDirs"].unshift(nodeData.text);
        }
        var currId = nodeData.parent;
        nodeData = tree.get_node(currId);
    }
    callback(preData);
};

var getKFromClusterInputFunction = function(callbackWithValue, obj) {
    // get user input
    var $input = $('#k-for-clustering');
    var $container = $('#k-for-clustering-overlay');
    var $inner = $('#k-for-clustering-inside');
    var $submit = $('#k-for-clustering-submit');
    var $cancel = $('#k-for-clustering-cancel');

    $input.val('');
    $container.show();
    $submit.off('click');
    $cancel.off('click');
    $input.off('click');
    $inner.off('click');

    $inner.click(function(e){
        e.stopPropagation();
    });
    $submit.click(function() {
        $container.hide();
        callbackWithValue($input.val());
    });
    $cancel.click(function() {
        $container.hide();
        $(obj.reference).removeClass('spinner').off('contextmenu');
    });
    $container.click(function() {
        $container.hide();
        $(obj.reference).removeClass('spinner').off('contextmenu');
    });

}

var selectionDatasetDataFunction = function(tree,node,name,deletable,callback,obj) {
    // get user input
    var assets = null;
    if($(document.body).children('.lightbox').length>0) {
        assets = Array.from(previewSelectionCache);
    } else {
        assets = Array.from(selectionCache);
    }
    var preData = {};
    if(assets.length==0) {
        preData['emptyDataset'] = true;
        alert('Warning: No assets have been selected.');
    }
    preData["name"]=name;
    preData["assets"] = assets;
    preData["parentDirs"] = [];
    preData["deletable"] = deletable;
    if(node.hasOwnProperty('data') && node.data.hasOwnProperty('file')) {
        preData["file"] = node.data.file;
    }
    if(node.hasOwnProperty('data') && node.data.hasOwnProperty('user')) {
        preData["user"] = node.data.user;
    }
    var nodeData = node;
    while(typeof nodeData.text !== 'undefined') {
        if(nodeData.type==='folder') {
            preData["parentDirs"].unshift(nodeData.text);
        }
        var currId = nodeData.parent;
        nodeData = tree.get_node(currId);
    }
    callback(preData);
};

var assetListDatasetDataFunction = function(tree,node,name,deletable,callback,obj) {
    // get user input
    var $input = $('#new-dataset-from-asset-list');
    var $container = $('#new-dataset-from-asset-list-overlay');
    var $submit = $('#new-dataset-from-asset-list-submit');
    var $cancel = $('#new-dataset-from-asset-list-cancel');
    var $inner = $('#new-dataset-from-asset-list-inside');

    $input.val('');
    $container.show();
    $submit.off('click');
    $cancel.off('click');
    $input.off('click');
    $inner.off('click');

    $inner.click(function(e){
        e.stopPropagation();
    });
    $submit.click(function() {
        var preData = {};
        preData["name"]=name;
        preData["assets"] = $input.val().split(/\s+/);
        preData["parentDirs"] = [];
        preData["deletable"] = deletable;
        if(node.hasOwnProperty('data') && node.data.hasOwnProperty('file')) {
            preData["file"] = node.data.file;
        }
        if(node.hasOwnProperty('data') && node.data.hasOwnProperty('user')) {
            preData["user"] = node.data.user;
        }
        var nodeData = node;
        while(typeof nodeData.text !== 'undefined') {
            if(nodeData.type==='folder') {
                preData["parentDirs"].unshift(nodeData.text);
            }
            var currId = nodeData.parent;
            nodeData = tree.get_node(currId);
        }
        $container.hide();
        callback(preData);
    });

    $cancel.click(function() {
        $container.hide();
        $(obj.reference).removeClass('spinner').off('contextmenu');
    });
    $container.click(function() {
        $(obj.reference).removeClass('spinner').off('contextmenu');
        $container.hide();
    });
};

var saveJSNodeFunction = function(tree,node,name,deletable,preData,node_type,create,skipSuccessFunction,callback,onlyUpdate){
    if(preData!==null) {
        preData['defaultFile'] = skipSuccessFunction;
        preData['addToAssets'] = onlyUpdate;
        var urlPrefix = $('#url-prefix').attr('prefix');
        if(!urlPrefix) {
            urlPrefix = "/secure";
        }
        $.ajax({
            type: "POST",
            url: urlPrefix+'/save_'+node_type,
            data: preData,
            error: function(jqxhr,status,error) {
                if(jqxhr.status==404) {
                   alert("Unable to establish connection to platform. Try refreshing page.");
                }
            },
            success: function(data) {
                if(callback!==null) {
                    callback();
                }
                if(skipSuccessFunction) return;

                if(!data.hasOwnProperty('file')&&!data.hasOwnProperty('user')) {
                    alert('Error saving template: '+data.message);
                } else {
                    preData['file']=data['file'];
                    preData['user']=data['user'];
                    var newData = {
                        'text': name,
                        'type': 'file',
                        'icon': 'jstree-file',
                        'jstree': {'type': 'file'},
                    };
                    $.each(preData, function(k,v) { newData[k] = v; });
                    if(create) {
                        node = tree.create_node(
                            node,
                            { 'data' : newData},
                            'first',
                            function(newNode) {
                                setTimeout(function() {
                                    var refresh = false;
                                    if(node_type==='dataset' && data.hasOwnProperty('asset_count')) {
                                        // update asset counts
                                        //$('#'+newNode.id).attr('data-assetcount', data['asset_count']);
                                        newData['assetcount'] = data['asset_count'];
                                        refresh = true;
                                    }
                                    newNode.data = newData;
                                    if(refresh) {
                                        tree.redraw(true);
                                    }
                                    tree.edit(newNode,name,function(n,status,cancelled) {
                                        if(status && ! cancelled) {
                                            renameJSNodeFunction(tree,n,n.text,data['file'],node_type);
                                        }
                                    });
                                },0);
                            }
                        );
                    } else {
                        var refresh = false;
                        if(node_type==='dataset' && data.hasOwnProperty('asset_count')) {
                            // update asset counts
                            newData['assetcount'] = data['asset_count'];
                            refresh = true;
                        }
                        node.data = newData;
                        if(refresh) {
                            tree.redraw(true);
                        }
                    }
                }
            },
            dataType: "json"
        });
    }
    return false;
};

var removeDescendantsHelper = function(tree,node,node_type) {
    var isFolder = node.type==='folder';
    if(isFolder) {
        // get all children
        var children = node.children;
        for(var i = 0; i < children.length; i++) {
            var child = tree.get_node(children[i]);
            removeDescendantsHelper(tree,child,node_type);
        }
        tree.delete_node(node);
    } else {
        removeJSNodeFunction(tree,node,node.data.file,node_type)
    }
};

var renameDescendantsOfFolderHelper = function(tree,node,node_type) {
    var isFolder = node.type==='folder';
    if(isFolder) {
        // get all children
        var children = node.children;
        for(var i = 0; i < children.length; i++) {
            var child = tree.get_node(node.children[i]);
            renameDescendantsOfFolderHelper(tree,child,node_type);
        }

    } else {
        renameJSNodeFunction(tree,node,node.text,node.data.file,node_type);
    }
};

var loadEvent = function(){
 // do nothing :(
};

function capitalize(string)
{
    return string.charAt(0).toUpperCase() + string.slice(1);
}


function cleanArray(actual) {
  var newArray = new Array();
  for (var i = 0; i < actual.length; i++) {
    if (actual[i]) {
      newArray.push(actual[i]);
    }
  }
  return newArray;
}

var setupJSTree = function(tree_id, dblclickFunction, node_type, jsNodeDataFunctions, newItemSubLabels) {
    $(tree_id)
    .bind('loaded.jstree', function() {
        $(tree_id).find('li:first i.jstree-icon').first().trigger('click');
    })
    .jstree({
        "core" : {
            "multiple" : false,
            "check_callback": true
        },
        "contextmenu": {
            "items": function(node) {

                var items = {};
                var tree = $(tree_id).jstree(true);

                var isFolder = node.type==='folder';
                var topLevelFolder = isFolder && (node.parents.length === 1);
                var deletable = node.data.deletable;

                if(isFolder && deletable) {
                    items["New Folder"] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "New Folder",
                        "title": "Create a new subdirectory.",
                        "action": function(obj) {
                            node = tree.create_node(node, {
                                'text': 'New Folder',
                                'type': 'folder',
                                'icon': 'jstree-folder',
                                'jstree': {'type': 'folder'},
                                'data' : {
                                    'deletable': deletable
                                }
                            });
                            tree.edit(node);
                        }
                    };
                    // must create a folder first in the shared environment
                    if(!(topLevelFolder && node.text.startsWith("Shared"))) {
                        var menuName = "New "+capitalize(node_type);
                        var subMenu = {};
                        var labelToFunctions = {};
                        for(var i = 0; i < jsNodeDataFunctions.length; i++) {
                            var jsNodeDataFunction = jsNodeDataFunctions[i];
                            var newItemSubLabel = newItemSubLabels[i];
                            if (newItemSubLabel==='From Selection') {
                                var val = null;
                                if($(document.body).children('.lightbox').length>0) {
                                    val = previewSelectionCache.size.toString();
                                } else {
                                    val = selectionCache.size.toString();
                                }
                                if (!val) { val = "0"; }
                                newItemSubLabel += ' ('+val+")";
                            }
                            labelToFunctions[newItemSubLabel]=jsNodeDataFunction;
                            subMenu[newItemSubLabel] = {
                                "separator_before": false,
                                "separator_after": false,
                                "label": newItemSubLabel,
                                "title": "Create new "+node_type+" "+newItemSubLabel.toLowerCase()+".",
                                "action": function(obj) {
                                    var name = 'New '+capitalize(node_type);
                                    $(obj.reference).addClass('spinner').on('contextmenu', function(e) { e.preventDefault(); e.stopPropagation(); return false; });
                                    var callback = function(data) {
                                        saveJSNodeFunction(tree,node,name,deletable,data,node_type,true,false,null,false);
                                        $(obj.reference).removeClass('spinner').off('contextmenu');
                                        notify_success('Successfully created '+node_type+'.');
                                    };
                                    labelToFunctions[obj.item.label](tree,node,name,deletable,callback,obj);
                                    return true;
                                }
                            }
                        }
                        items[menuName] = {
                            "separator_before": false,
                            "separator_after": false,
                            "label": menuName,
                            "title": "Create a new "+node_type+".",
                            "submenu": subMenu
                        };
                    }
                }
                if(!topLevelFolder && deletable) {
                    items["Delete"] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "Delete",
                        "title": "Permanently delete this "+(isFolder ? "folder" : node_type)+".",
                        "action": function(obj) {
                            removeDescendantsHelper(tree,node,node_type);
                            return true;
                        }
                    };
                    items["Rename"] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "Rename",
                        "title": "Rename this "+(isFolder ? "folder" : node_type)+".",
                        "action": function(obj) {
                            if(isFolder) {
                                tree.edit(node,node.text,function(node,status,cancelled) {
                                    renameDescendantsOfFolderHelper(tree,node,node_type);
                                });
                            } else {
                                tree.edit(node,node.text,function(node,status,cancelled) {
                                    if(status && ! cancelled) {
                                        renameJSNodeFunction(tree,node,node.text,node.data.file,node_type);
                                    }
                                })
                            }
                            return true;
                        }
                    };
                    if(!isFolder) {
                        var menuName = "Apply "+capitalize(node_type);
                        items[menuName] = {
                            "separator_before": false,
                            "separator_after": false,
                            "label": menuName,
                            "title": "Apply this "+node_type+".",
                            "action": function(obj) {
                                if(node.type==='file') {
                                    dblclickFunction(node.data,tree,node);
                                    return true;
                                }
                            }
                        };
                        var menuName = "Replace "+capitalize(node_type);
                        var subMenu = {};
                        var labelToFunctions = {};
                        for(var i = 0; i < jsNodeDataFunctions.length; i++) {
                            var jsNodeDataFunction = jsNodeDataFunctions[i];
                            var newItemSubLabel = newItemSubLabels[i];
                            if (newItemSubLabel==='From Selection') {
                                var val = null;
                                if($(document.body).children('.lightbox').length>0) {
                                    val = previewSelectionCache.size.toString();
                                } else {
                                    val = selectionCache.size.toString();
                                }
                                if (!val) { val = "0"; }
                                newItemSubLabel += ' ('+val+")";
                            }
                            labelToFunctions[newItemSubLabel]=jsNodeDataFunction;
                            subMenu[newItemSubLabel] = {
                                "separator_before": false,
                                "separator_after": false,
                                "label": newItemSubLabel,
                                "title": "Replace this "+node_type+" "+newItemSubLabel.toLowerCase()+".",
                                "action": function(obj) {
                                    var name = node.text;
                                    $(obj.reference).addClass('spinner').on('contextmenu', function(e) { e.preventDefault(); e.stopPropagation(); return false; });
                                    var callback = function(data) {
                                        saveJSNodeFunction(tree,node,name,deletable,data,node_type,false,false,null,false);
                                        $(obj.reference).removeClass('spinner').off('contextmenu');
                                        notify_success('Successfully replaced '+node_type+'.');
                                    };
                                    labelToFunctions[obj.item.label](tree,node,name,deletable,callback, obj);
                                    return true;
                                }
                            }
                        }
                        items[menuName] = {
                            "separator_before": false,
                            "separator_after": false,
                            "label": menuName,
                            "title": "Replace this "+node_type+". Caution: Existing "+node_type+" will be deleted.",
                            "submenu": subMenu
                        };
                    }
                }
                if((node_type==='dataset') && isFolder) {
                    // plot children
                    var menuName = "Apply Children";
                    items[menuName] = {
                        "separator_before": true,
                        "separator_after": false,
                        "label": menuName,
                        "title": "Apply child datasets to current template.",
                        "action": function(obj) {
                            showMultipleDatasetFunction(node.data,tree,node);
                            return true;
                        }

                    };
                    // plot children
                    var menuName = "Add Children";
                    items[menuName] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": menuName,
                        "title": "Add child datasets to current template.",
                        "action": function(obj) {
                            addMultipleDatasetFunction(node.data,tree,node);
                            return true;
                        }

                    };

                }
                if((node_type==='dataset') && !isFolder && deletable) {
                    var addAssetsSubmenu = {};
                    var labelToFunctions = {};
                    for(var i = 0; i < jsNodeDataFunctions.length; i++) {
                        var jsNodeDataFunction = jsNodeDataFunctions[i];
                        var newItemSubLabel = newItemSubLabels[i];
                        if (newItemSubLabel==='From Selection') {
                            var val = null;
                            if($(document.body).children('.lightbox').length>0) {
                                val = previewSelectionCache.size.toString();
                            } else {
                                val = selectionCache.size.toString();
                            }
                            if (!val) { val = "0"; }
                            newItemSubLabel += ' ('+val+")";
                        }
                        labelToFunctions[newItemSubLabel]=jsNodeDataFunction;
                        addAssetsSubmenu[newItemSubLabel] = {
                            "separator_before": false,
                            "separator_after": false,
                            "label": newItemSubLabel,
                            "title": "Add assets to this existing dataset "+newItemSubLabel.toLowerCase()+".",
                            "action": function(obj) {
                                var name = node.text;
                                $(obj.reference).addClass('spinner').on('contextmenu', function(e) { e.preventDefault(); e.stopPropagation(); return false; });;
                                var callback = function(data) {
                                    saveJSNodeFunction(tree,node,name,deletable,data,node_type,false,false,null,true);
                                    $(obj.reference).removeClass('spinner').off('contextmenu');
                                    notify_success('Successfully added assets.');
                                };
                                labelToFunctions[obj.item.label](tree,node,name,deletable,callback, obj);
                                return true;
                            }
                        }
                    }
                    items["Add Assets"] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "Add Assets",
                        "title": "Add assets to this dataset.",
                        "submenu": addAssetsSubmenu
                    };
                    items["Cluster Dataset"] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "Cluster Dataset",
                        "title": "Cluster this dataset into technology categories.",
                        "action": function(obj) {
                            $(obj.reference).addClass('spinner').on('contextmenu', function(e) { e.preventDefault(); e.stopPropagation(); return false; });;
                            // num clusters
                            var callback = function(k) {
                                // need to get data
                                var nodeData = node;
                                var parents = [];
                                while(typeof nodeData.text !== 'undefined') {
                                    if(nodeData.type==='folder') {
                                        parents.unshift(nodeData.text);
                                    }
                                    var currId = nodeData.parent;
                                    nodeData = tree.get_node(currId);
                                }
                                var shared = parents.length > 0 && parents[0].startsWith("Shared");
                                var urlPrefix = $('#url-prefix').attr('prefix');
                                if(!urlPrefix) {
                                    urlPrefix = "/secure";
                                }
                                $.ajax({
                                    type: "POST",
                                    url: urlPrefix+'/cluster_dataset',
                                    data: {
                                        file: node.data.file,
                                        shared: shared,
                                        k: k
                                    },
                                    success: function(clusters) {
                                        if(!clusters.hasOwnProperty('clusters')) {
                                             alert('Error saving template: '+clusters.message);
                                        } else {
                                            var folderData = {
                                                'text': node.text,
                                                'deletable': true,
                                                'type': 'folder',
                                                'icon': 'jstree-folder',
                                                'jstree': {'type':'folder'}
                                            };
                                            tree.create_node(
                                                tree.get_node(node.parent),
                                                {'data' : folderData, 'text': node.text},
                                                'first',
                                                function(newFolder) {
                                                    newFolder.data=folderData;
                                                    $.each(clusters.clusters, function(idx,data){
                                                        if(data.hasOwnProperty('file')&&data.hasOwnProperty('user')&&data.hasOwnProperty('name')) {
                                                            var newData = {
                                                                'text': data.name,
                                                                'deletable': true,
                                                                'type': 'file',
                                                                'icon': 'jstree-file',
                                                                'jstree': {'type': 'file'},
                                                            };
                                                            $.each(data, function(k,v) { newData[k] = v; });
                                                            var newNode = tree.create_node(
                                                                newFolder,
                                                                { 'data' : newData, 'text': data.name},
                                                                'first',
                                                                function(newNode) {
                                                                    newNode.data=newData;
                                                                }
                                                            );
                                                        }
                                                    });
                                                    $(obj.reference).removeClass('spinner').off('contextmenu');
                                                    notify_success('Successfully finished clustering.');
                                                }
                                            );

                                        }
                                    },
                                    dataType: "json"
                                });
                            };
                            getKFromClusterInputFunction(callback, obj);
                            return true;
                        }
                    };
                }
                if((node_type==='dataset') && isFolder && deletable && !topLevelFolder) {
                    items["Build Datasets from Assignee List"] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "Build Datasets from Assignee List",
                        "title": "Creates a new folder with a dataset sample for each assignee in a list",
                        "action": function(obj) {
                            // start spinner
                            $(obj.reference).addClass('spinner').on('contextmenu', function(e) { e.preventDefault(); e.stopPropagation(); return false; });;
                            var callback = function(assignees) {
                                // need to get data
                                var nodeData = node;
                                var parents = [];
                                while(typeof nodeData.text !== 'undefined') {
                                    if(nodeData.type==='folder') {
                                        parents.unshift(nodeData.text);
                                    }
                                    var currId = nodeData.parent;
                                    nodeData = tree.get_node(currId);
                                }
                                var shared = parents.length > 0 && parents[0].startsWith("Shared");
                                var urlPrefix = $('#url-prefix').attr('prefix');
                                if(!urlPrefix) {
                                    urlPrefix = "/secure";
                                }
                                $.ajax({
                                    type: "POST",
                                    url: urlPrefix+'/assignee_datasets',
                                    data: {
                                        parentDirs: parents,
                                        assignees: assignees,
                                        shared: shared
                                    },
                                    success: function(clusters) {
                                        if(!clusters.hasOwnProperty('assignees')) {
                                             alert('Error saving template: '+clusters.message);
                                        } else {
                                            $.each(clusters.assignees, function(idx,data){
                                                if(data.hasOwnProperty('file')&&data.hasOwnProperty('user')&&data.hasOwnProperty('name')) {
                                                    var newData = {
                                                        'text': data.name,
                                                        'deletable': true,
                                                        'type': 'file',
                                                        'icon': 'jstree-file',
                                                        'jstree': {'type': 'file'},
                                                    };
                                                    $.each(data, function(k,v) { newData[k] = v; });
                                                    var newNode = tree.create_node(
                                                        node,
                                                        { 'data' : newData, 'text': data.name},
                                                        'first',
                                                        function(newNode) {
                                                            newNode.data=newData;
                                                        }
                                                    );
                                                }
                                            });
                                            $(obj.reference).removeClass('spinner').off('contextmenu');
                                            notify_success('Successfully finished building assignee datasets.');
                                        }
                                    },
                                    dataType: "json"
                                });
                            };

                            // get user input
                            var $input = $('#create-assignee-datasets');
                            var $container = $('#create-assignee-datasets-overlay');
                            var $inner = $('#create-assignee-datasets-inside');
                            var $submit = $('#create-assignee-datasets-submit');
                            var $cancel = $('#create-assignee-datasets-cancel');

                            $input.val('');
                            $container.show();
                            $submit.off('click');
                            $cancel.off('click');
                            $input.off('click');
                            $inner.off('click');

                            $inner.click(function(e){
                                e.stopPropagation();
                            });
                            $submit.click(function() {
                                $container.hide();
                                callback($input.val());
                            });
                            $cancel.click(function() {
                                $container.hide();
                                $(obj.reference).removeClass('spinner').off('contextmenu');
                            });
                            $container.click(function() {
                                $container.hide();
                                $(obj.reference).removeClass('spinner').off('contextmenu');
                            });

                            return true;
                        }
                    };

                }
                return items;
            }
        },
        node_customize: {
            default: function(elem, node) {
                if(node && node_type==='dataset' && node.hasOwnProperty('type') && node.type==='file' && node.hasOwnProperty('data') && node.data.hasOwnProperty('assetcount')) { // check node itself
                    var $anchor = $(elem).find('a');
                    if($anchor && $anchor.length>0) {
                        // hack to add icon back
                        $(elem).find('i.jstree-icon').attr('class', 'jstree-icon jstree-themeicon jstree-file jstree-themeicon-custom')
                            .css('margin-left', '20px');
                        var text = $anchor.text();
                        if(text && text.trim().length > 0) {
                            $anchor.text(text+' - ('+node.data['assetcount']+')');
                        } else {
                            $anchor.text(text);
                        }
                    }
                }
            }
        },
        "types": {
            "folder": {
                "icon": "jstree-folder"
            },
            "file" : {
                "icon": "jstree-file"
            }
        },
        "plugins": ["types","wholerow","sort","contextmenu","node_customize"]
    });

    $(tree_id).bind("dblclick.jstree", function(event) {
        if( $('input:focus').length == 0 ) {
            var tree = $(this).jstree(true);
            var node = tree.get_node(event.target);
            if(node.type==='file') {
                $('#data-tab-link').click();
                $('#filters-link').click();
                event.preventDefault();
                event.stopPropagation();
                dblclickFunction(node.data,tree,node);
                return false;
            }
        }
        return true;
    });

/*    if(node_type==='dataset') {
        var addAssetCountFunc = function(e, data) {
            if(data.hasOwnProperty('node')) {
                var tree = $(tree_id).jstree(true);
                var node = data.node;
                if(node.type==='file' && node.hasOwnProperty('data') && node.data.hasOwnProperty('assetcount')) { // check node itself
                    var $anchor = $('#'+node.id+'_anchor');
                    if($anchor.length) {
                        $anchor.prev().text(node.data['assetcount']);
                    }
                } // check children
                if(node.type==='folder' && node.children && node.children.length>0) {
                    for(var i = 0; i < node.children.length; i++) {
                        var child = node.children[i];
                        child = tree.get_node(child);
                        if(child.data.hasOwnProperty('assetcount')) {
                            var $anchor = $('#'+child.id+'_anchor');
                            if($anchor.length) {
                                $anchor.prev().text(child.data['assetcount']);
                            }
                        }
                    }
                }
            }
        };
        $(tree_id).bind('changed.jstree', addAssetCountFunc);

        $(tree_id).bind('rename_node.jstree', function(e, node) {
            var data = { 'node': node.node };
            addAssetCountFunc(e, data);
        });
        $(tree_id).bind('open_node.jstree', function(e, node) {
            var data = { 'node': node.node };
            addAssetCountFunc(e, data);
        });
        $(tree_id).bind('create_node.jstree', function(e, node) {
            var data = { 'node': node.node };
            addAssetCountFunc(e, data);
        });
    }
*/
/*    $(tree_id).bind("open_node.jstree", function(event,data) {
        $('ul.vakata-context.jstree-contextmenu li a[title]')
            .tooltip({
                placement: 'right',
                trigger: 'hover',
                delay: {
                    "show": 400,
                    "hide": 200
                },
                html: true
            });
        return true;
    });
*/
};