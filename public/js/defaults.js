$(document).ready(function() {
    var resetCheckbox = function(elem,target) {
        var $draggable = $(elem);
        $draggable.detach().css({top: 0,left: 0}).appendTo(target);
        var shouldShow = $(target).hasClass('target');
        $toggle = $draggable.find(".collapse");
        $handle = $draggable.find(".handle");
        if(shouldShow) {
            if($handle.attr('data-hidden-target')) {
                $handle.attr('data-target',$handle.attr('data-hidden-target'));
                $handle.removeAttr('data-hidden-target');
            }
            if(! $toggle.hasClass('show')) {
                $toggle.addClass('show');
            }
        } else {
            if($handle.attr('data-target')) {
                $handle.attr('data-hidden-target',$handle.attr('data-target'));
                $handle.removeAttr('data-target');
            }
            if($toggle.hasClass('show')) {
                $toggle.removeClass('show');
            }
        }
        var $checkbox = $draggable.find(".mycheckbox")
        $checkbox.prop("checked", shouldShow);
        $checkbox.prop("disabled", !shouldShow);
    }

    var dropFunc = function(event, ui) {
        resetCheckbox(ui.draggable,this);
    };

    $('.draggable').draggable({
        revert: true
    });

    $('.droppable.filters').droppable({
        accept: '.draggable.filters',
        drop: dropFunc
    });

    $(".mycheckbox").on("click", function (e) {
        var checkbox = $(this);
        // do the confirmation thing here
        e.preventDefault();
        return false;
    });

    $('.handle-wrapper').dblclick(function(e) {
        e.stopPropagation();
        return false;
    });

    $('.droppable.values').droppable({
        accept: '.draggable.values',
        drop: dropFunc
    });

    $('.droppable.attributes').droppable({
        accept: '.draggable.attributes',
        drop: dropFunc
    });

    $('.droppable.charts').droppable({
        accept: '.draggable.charts',
        drop: dropFunc
    });

    $('.draggable .double-click').dblclick(function() {
        var $draggable = $(this).parent();
        var id = $draggable.data('target');
        if(id) {
            var target;
            $parent = $draggable.parent();
            if($parent.hasClass('target') || $parent.parent().hasClass('target')) {
                target = "start";
            } else {
                target = "target";
            }
            $target = $('#'+id+'-'+target);
            if($target) {
                  resetCheckbox($draggable.get(0),$target.get(0));
            }
        }
    });

    $('.multiselect').multiselect({
        enableFiltering: true,
        enableCaseInsensitiveFiltering: true,
        maxHeight: 300
    });

    var f = -1;
    $('th.sortable').on("click",function() {
        var sortOnField = $(this).data('field');
        alert(sortOnField);
        var $wrapper = $('#data-table').find('tbody');
        var rows = $wrapper.find("tr").get();

        rows.sort(function(a, b) {

            var A = getVal(a,sortOnField);
            var B = getVal(b,sortOnField);

            if(A < B) {
                return -1*f;
            }
            if(A > B) {
                return 1*f;
            }
            return 0;
        });


        $.each(rows, function(){
            $wrapper.append(this);
        });
    });

    var getVal = function (elm,sortOnField) {
        var v = $(elm).data(sortOnField);
        alert(v);
        if($.isNumeric(v)){
            v = parseInt(v,10);
        }
        return v;
    };

});
