﻿namespace Kendo.Mvc.Examples.Controllers
{
    using System;
    using System.Collections.Generic;
    using System.Linq;
    using System.Web;
    using System.Web.Mvc;
    using Kendo.Mvc.UI;
    using Kendo.Mvc.Examples.Models.Scheduler;

    public partial class SchedulerController : SchedulerEventController<Task>
    {
        public SchedulerController()
            : base(new SchedulerEventService<Task>())
        {
        }

        public ActionResult Index()
        {
            return View();
        }

    }
}
